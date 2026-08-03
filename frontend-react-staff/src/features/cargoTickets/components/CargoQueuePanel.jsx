import { useState } from 'react';
import { Alert, Badge, Button, Modal, Table } from 'react-bootstrap';
import { BsCashCoin, BsEye, BsPencil, BsPrinter, BsTelephone, BsTrash } from 'react-icons/bs';
import Pagination from '../../../components/common/Pagination';
import { formatCurrency } from '../../../utils/formatters';
import { useCargoTickets } from '../hooks/useCargoTickets';
import { cargoTicketApi } from '../api/cargoTicketApi';
import { canPrintCargoTicket } from '../utils/canPrintCargoTicket';
import { printCargoTicket } from '../utils/printCargoTicket';
import CargoTicketUpdateModal from './CargoTicketUpdateModal';
import CargoTicketDetailViewModal from './CargoTicketDetailViewModal';
import CargoConfirmModal from './CargoConfirmModal';
import QrPaymentModal from './QrPaymentModal';
import EditPaymentMethodModal from './EditPaymentMethodModal';

const QUEUE_STATUS_PRESENTATION = {
    RECEIVED: { label: 'Đang chờ', badge: 'primary' },
    ARRIVED: { label: 'Chờ giao hàng', badge: 'warning' },
    DELIVERED: { label: 'Đã giao hàng', badge: 'success' }
};

const PAYMENT_STATUS_LABEL = {
    PENDING: 'Chờ thanh toán',
    COMPLETED: 'Đã thanh toán',
    FAILED: 'Thất bại'
};

const PAYMENT_STATUS_BADGE = {
    PENDING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger'
};

const FEE_PAYER_LABEL = {
    SENDER: 'Người gửi trả',
    RECEIVER: 'Người nhận trả'
};

function isPaymentPending(ticket) {
    return ticket?.payment?.status === 'PENDING';
}

function isBankPending(ticket) {
    return isPaymentPending(ticket) && ticket.payment?.paymentMethod === 'BANK_TRANSFER';
}

function isCashPending(ticket) {
    return isPaymentPending(ticket) && ticket.payment?.paymentMethod === 'CASH';
}

function needsReceiverPaymentChoice(ticket) {
    return ticket.feePayer === 'RECEIVER' && !ticket.payment;
}

/** Payment exists but is not yet completed — staff can still switch the method. */
function canEditPaymentMethod(ticket) {
    return ticket.payment && ticket.payment.status !== 'COMPLETED';
}

function canConfirmDeliver(ticket) {
    if (ticket.feePayer !== 'RECEIVER') return true;
    // Method not chosen yet — staff picks cash/bank at hand-off.
    if (!ticket.payment) return true;
    if (ticket.payment.status === 'COMPLETED') return true;
    return ticket.payment.paymentMethod === 'CASH';
}

/**
 * Shared operational queue. Pending orders expose update/cancel actions while
 * arrived orders expose only read-only detail and receiver confirmation.
 */
export default function CargoQueuePanel({
    status,
    tripId = null,
    editable = false,
    confirmable = false,
    agencyStopId = null,
    onQueueChanged
}) {
    const { tickets, loading, error, pageInfo, setPageInfo, refetch } = useCargoTickets(status, tripId);
    const [editTicket, setEditTicket] = useState(null);
    const [viewTicket, setViewTicket] = useState(null);
    const [qrTicket, setQrTicket] = useState(null);
    const [actionId, setActionId] = useState(null);
    const [confirmDialog, setConfirmDialog] = useState(null);
    const [receiverPayTicket, setReceiverPayTicket] = useState(null);
    const [editPaymentTicket, setEditPaymentTicket] = useState(null);
    // Edit-payment modal manages its own busy state internally.

    const busy = actionId != null;
    const qrOpenFor = (ticket) => qrTicket?.cargoTicketId === ticket.cargoTicketId;

    const closeConfirm = () => {
        if (actionId != null) return;
        setConfirmDialog(null);
    };

    const closeReceiverPay = () => {
        if (actionId != null) return;
        setReceiverPayTicket(null);
    };



    const runAction = async (id, action) => {
        setActionId(id);
        try {
            await action();
            await refetch();
            await onQueueChanged?.();
            setConfirmDialog(null);
        } catch (requestError) {
            window.alert(requestError.response?.data?.message || 'Không thể cập nhật đơn hàng.');
        } finally {
            setActionId(null);
        }
    };

    const openQr = async ticket => {
        try {
            const full = await cargoTicketApi.getCargoTicket(ticket.cargoTicketId);
            setQrTicket(full || ticket);
        } catch {
            setQrTicket(ticket);
        }
    };

    const requestCancel = (ticket) => {
        if (busy || qrOpenFor(ticket)) return;
        const paidNote = ticket.payment?.status === 'COMPLETED'
            ? '\nĐơn đã thanh toán sẽ tạo yêu cầu hoàn tiền.'
            : '';
        setConfirmDialog({
            type: 'cancel',
            ticket,
            title: 'Hủy đơn gửi hàng',
            message: `Bạn có chắc muốn hủy đơn ${ticket.ticketCode}?${paidNote}`,
            confirmLabel: 'Hủy đơn',
            confirmVariant: 'danger',
        });
    };

    const requestDeliver = (ticket) => {
        if (busy) return;
        if (needsReceiverPaymentChoice(ticket)) {
            setReceiverPayTicket(ticket);
            return;
        }
        if (ticket.feePayer === 'RECEIVER' && isBankPending(ticket)) {
            window.alert('Người nhận chưa chuyển khoản xong. Vui lòng mở QR và chờ thanh toán thành công trước.');
            openQr(ticket);
            return;
        }
        if (ticket.feePayer === 'RECEIVER' && isCashPending(ticket)) {
            setConfirmDialog({
                type: 'deliver-cash',
                ticket,
                title: 'Xác nhận thu tiền mặt',
                message:
                    `Xác nhận đã nhận đủ tiền mặt từ người nhận (${ticket.receiverName}) `
                    + `cho đơn ${ticket.ticketCode}?\nSố tiền: ${formatCurrency(ticket.totalPrice)}\n`
                    + 'Sau khi xác nhận, đơn sẽ được đánh dấu đã giao hàng.',
                confirmLabel: 'Đã nhận tiền — giao hàng',
                confirmVariant: 'success',
            });
            return;
        }
        setConfirmDialog({
            type: 'deliver',
            ticket,
            title: 'Xác nhận giao hàng',
            message: `Xác nhận ${ticket.receiverName} đã nhận đơn ${ticket.ticketCode}?`,
            confirmLabel: 'Đã giao hàng',
            confirmVariant: 'success',
        });
    };

    const chooseReceiverCash = () => {
        if (!receiverPayTicket) return;
        const ticket = receiverPayTicket;
        setReceiverPayTicket(null);
        setConfirmDialog({
            type: 'deliver-cash',
            ticket,
            paymentMethod: 'CASH',
            title: 'Xác nhận thu tiền mặt',
            message:
                `Xác nhận đã nhận đủ tiền mặt từ người nhận (${ticket.receiverName}) `
                + `cho đơn ${ticket.ticketCode}?\nSố tiền: ${formatCurrency(ticket.totalPrice)}\n`
                + 'Sau khi xác nhận, đơn sẽ được đánh dấu đã giao hàng.',
            confirmLabel: 'Đã nhận tiền — giao hàng',
            confirmVariant: 'success',
        });
    };

    const chooseReceiverBank = async () => {
        if (!receiverPayTicket) return;
        const ticket = receiverPayTicket;
        setActionId(ticket.cargoTicketId);
        try {
            const updated = await cargoTicketApi.chooseReceiverPaymentMethod(
                ticket.cargoTicketId,
                { paymentMethod: 'BANK_TRANSFER' }
            );
            setReceiverPayTicket(null);
            await refetch();
            await onQueueChanged?.();
            setQrTicket(updated || ticket);
        } catch (requestError) {
            window.alert(requestError.response?.data?.message || 'Không thể chọn chuyển khoản.');
        } finally {
            setActionId(null);
        }
    };

    const requestCollectCash = (ticket) => {
        if (busy) return;
        setConfirmDialog({
            type: 'collect-cash',
            ticket,
            title: 'Xác nhận thu tiền mặt',
            message:
                `Xác nhận đã nhận đủ tiền mặt cho đơn ${ticket.ticketCode}?\n`
                + `Số tiền: ${formatCurrency(ticket.totalPrice)}`,
            confirmLabel: 'Đã nhận tiền',
            confirmVariant: 'success',
        });
    };

    const handleConfirm = async () => {
        if (!confirmDialog?.ticket) return;
        const { type, ticket } = confirmDialog;
        if (type === 'cancel') {
            await runAction(ticket.cargoTicketId, () => cargoTicketApi.disableCargoTicket(ticket.cargoTicketId));
            return;
        }
        if (type === 'deliver' || type === 'deliver-cash') {
            const body = confirmDialog.paymentMethod
                ? { paymentMethod: confirmDialog.paymentMethod }
                : undefined;
            await runAction(ticket.cargoTicketId, () => cargoTicketApi.confirmReceived(ticket.cargoTicketId, body));
            return;
        }
        if (type === 'collect-cash') {
            await runAction(ticket.cargoTicketId, () => cargoTicketApi.completePayment(ticket.cargoTicketId));
        }
    };

    if (loading) return <div className="cargo-loading">Đang tải đơn hàng...</div>;
    return <section className="cargo-queue-card">
        {error && <Alert variant="danger">{error}</Alert>}
        <Table responsive hover className="cargo-queue-table align-middle mb-0">
            <thead>
                <tr>
                    <th>Mã đơn</th>
                    <th>Người gửi / nhận</th>
                    <th>Hành trình</th>
                    <th>Chuyến xe</th>
                    <th>Trách nhiệm</th>
                    <th>Tiền / thanh toán</th>
                    <th>Hành động</th>
                </tr>
            </thead>
            <tbody>
                {tickets.length === 0 && (
                    <tr><td colSpan="7" className="cargo-empty">Không có đơn hàng trong trạng thái này.</td></tr>
                )}
                {tickets.map(ticket => (
                    <tr key={ticket.cargoTicketId}>
                        <td>
                            <strong>{ticket.ticketCode}</strong>
                            <div>
                                <Badge bg={(QUEUE_STATUS_PRESENTATION[ticket.status] || QUEUE_STATUS_PRESENTATION[status])?.badge || 'secondary'}>
                                    {(QUEUE_STATUS_PRESENTATION[ticket.status] || QUEUE_STATUS_PRESENTATION[status])?.label || ticket.status}
                                </Badge>
                            </div>
                        </td>
                        <td>
                            <Contact label="Gửi" name={ticket.senderName} phone={ticket.senderPhone} />
                            <Contact label="Nhận" name={ticket.receiverName} phone={ticket.receiverPhone} />
                        </td>
                        <td>
                            <strong>{ticket.routeName || '—'}</strong>
                            <small>{ticket.pickupStopName} → {ticket.dropoffStopName}</small>
                            <small>Văn phòng nhận: {ticket.destinationAgencyName || 'Chưa gán'}</small>
                        </td>
                        <td>
                            <strong>{ticket.licensePlate || 'Chưa gán xe'}</strong>
                            {editable && <small>Chuyến #{ticket.tripId || '—'}</small>}
                            {editable && !ticket.tripId && (
                                <div>
                                    <Badge bg="warning" text="dark">Chưa gán chuyến</Badge>
                                </div>
                            )}
                        </td>
                        <td>
                            <small>Tài xế: {ticket.driverName || '—'} · {ticket.driverPhone || '—'}</small>
                            <small>Phụ xe: {ticket.attendantName || '—'} · {ticket.attendantPhone || '—'}</small>
                        </td>
                        <td>
                            <strong>{formatCurrency(ticket.totalPrice)}</strong>
                            <small>{FEE_PAYER_LABEL[ticket.feePayer] || ticket.feePayer}</small>
                            {ticket.payment ? (
                                <div>
                                    <Badge bg={PAYMENT_STATUS_BADGE[ticket.payment.status] || 'secondary'}>
                                        {PAYMENT_STATUS_LABEL[ticket.payment.status] || ticket.payment.status}
                                    </Badge>
                                    <small className="d-block">
                                        {ticket.payment.paymentMethod === 'CASH' ? 'Tiền mặt' : 'Chuyển khoản'}
                                    </small>
                                </div>
                            ) : ticket.feePayer === 'RECEIVER' ? (
                                <small className="text-muted">Chọn lúc nhận hàng</small>
                            ) : (
                                <small>Chưa có thanh toán</small>
                            )}
                        </td>
                        <td>
                            <div className="cargo-actions">
                                <Button variant="outline-success" title="Xem đầy đủ" onClick={() => setViewTicket(ticket)}>
                                    <BsEye />
                                </Button>
                                {editable && canPrintCargoTicket(ticket, agencyStopId) && (
                                    <Button
                                        variant="outline-secondary"
                                        title="In tem gửi hàng"
                                        disabled={busy}
                                        onClick={() => printCargoTicket(ticket)}
                                    >
                                        <BsPrinter />
                                    </Button>
                                )}
                                {editable && (
                                    <Button
                                        variant="success"
                                        title="Cập nhật"
                                        disabled={busy || qrOpenFor(ticket)}
                                        onClick={() => setEditTicket(ticket)}
                                    >
                                        <BsPencil />
                                    </Button>
                                )}
                                {editable && isBankPending(ticket) && ticket.feePayer === 'SENDER' && (
                                    <Button
                                        variant="outline-primary"
                                        title="Thanh toán QR"
                                        disabled={busy}
                                        onClick={() => openQr(ticket)}
                                    >
                                        <BsCashCoin />
                                    </Button>
                                )}
                                {editable && isCashPending(ticket) && ticket.feePayer === 'SENDER' && (
                                    <Button
                                        variant="outline-primary"
                                        title="Xác nhận tiền mặt"
                                        disabled={busy}
                                        onClick={() => requestCollectCash(ticket)}
                                    >
                                        <BsCashCoin />
                                    </Button>
                                )}
                                {editable && (
                                    <Button
                                        variant="outline-danger"
                                        title="Hủy đơn"
                                        disabled={busy || qrOpenFor(ticket)}
                                        onClick={() => requestCancel(ticket)}
                                    >
                                        <BsTrash />
                                    </Button>
                                )}
                                {confirmable && needsReceiverPaymentChoice(ticket) && (
                                    <Button
                                        variant="outline-primary"
                                        title="Chọn hình thức thanh toán người nhận"
                                        disabled={busy}
                                        onClick={() => setReceiverPayTicket(ticket)}
                                    >
                                        <BsCashCoin />
                                    </Button>
                                )}
                                {confirmable && canEditPaymentMethod(ticket) && (
                                    <Button
                                        variant="outline-secondary"
                                        title="Đổi hình thức thanh toán"
                                        disabled={busy}
                                        onClick={() => setEditPaymentTicket(ticket)}
                                    >
                                        <BsPencil />
                                    </Button>
                                )}
                                {confirmable && isBankPending(ticket) && ticket.feePayer === 'RECEIVER' && (
                                    <Button
                                        variant="outline-primary"
                                        title="QR người nhận"
                                        disabled={busy}
                                        onClick={() => openQr(ticket)}
                                    >
                                        <BsCashCoin />
                                    </Button>
                                )}
                                {confirmable && (
                                    <Button
                                        className="cargo-primary-button text-nowrap"
                                        disabled={busy || !canConfirmDeliver(ticket)}
                                        onClick={() => requestDeliver(ticket)}
                                    >
                                        {needsReceiverPaymentChoice(ticket) ? 'Thu tiền / giao hàng' : 'Đã giao hàng'}
                                    </Button>
                                )}
                            </div>
                        </td>
                    </tr>
                ))}
            </tbody>
        </Table>
        <div className="cargo-pagination"><Pagination pageInfo={pageInfo} onPageChange={setPageInfo} /></div>
        {editTicket && (
            <CargoTicketUpdateModal
                key={editTicket.cargoTicketId}
                data={editTicket}
                onClose={() => { setEditTicket(null); refetch(); }}
                onSuccess={refetch}
            />
        )}
        {viewTicket && (
            <CargoTicketDetailViewModal
                key={viewTicket.cargoTicketId}
                ticket={viewTicket}
                onClose={() => { setViewTicket(null); refetch(); }}
                readOnly={!editable}
                canPrint={editable && canPrintCargoTicket(viewTicket, agencyStopId)}
            />
        )}
        {qrTicket && (
            <QrPaymentModal
                ticket={qrTicket}
                onClose={() => setQrTicket(null)}
                onSuccess={() => { setQrTicket(null); refetch(); onQueueChanged?.(); }}
            />
        )}
        <CargoConfirmModal
            show={Boolean(confirmDialog)}
            title={confirmDialog?.title || ''}
            message={confirmDialog?.message || ''}
            confirmLabel={confirmDialog?.confirmLabel}
            confirmVariant={confirmDialog?.confirmVariant || 'primary'}
            confirming={busy}
            onConfirm={handleConfirm}
            onCancel={closeConfirm}
        />
        <Modal show={Boolean(receiverPayTicket)} onHide={closeReceiverPay} centered backdrop="static">
            <Modal.Header closeButton={!busy}>
                <Modal.Title>Thanh toán người nhận</Modal.Title>
            </Modal.Header>
            <Modal.Body>
                <p className="mb-2">
                    Đơn <strong>{receiverPayTicket?.ticketCode}</strong> — người nhận trả phí.
                </p>
                <p className="mb-0 text-muted">
                    Số tiền: {formatCurrency(receiverPayTicket?.totalPrice)}. Chọn hình thức thanh toán tại văn phòng đích.
                </p>
            </Modal.Body>
            <Modal.Footer className="d-flex flex-wrap gap-2 justify-content-end">
                <Button variant="secondary" onClick={closeReceiverPay} disabled={busy}>
                    Đóng
                </Button>
                <Button variant="outline-primary" onClick={chooseReceiverBank} disabled={busy}>
                    {busy ? 'Đang xử lý...' : 'Chuyển khoản (QR)'}
                </Button>
                <Button variant="success" onClick={chooseReceiverCash} disabled={busy}>
                    Tiền mặt — giao hàng
                </Button>
            </Modal.Footer>
        </Modal>
        {editPaymentTicket && (
            <EditPaymentMethodModal
                ticket={editPaymentTicket}
                onClose={() => setEditPaymentTicket(null)}
                onSuccess={() => { setEditPaymentTicket(null); refetch(); onQueueChanged?.(); }}
            />
        )}
    </section>;
}

/** Renders one party without hiding the phone number needed for hand-over. */
function Contact({ label, name, phone }) {
    return (
        <div className="cargo-contact">
            <span>{label}</span>
            <strong>{name}</strong>
            <small><BsTelephone /> {phone}</small>
        </div>
    );
}
