import { useState } from 'react';
import { Alert, Button, Container } from 'react-bootstrap';
import { BsArrowLeft } from 'react-icons/bs';
import { useNavigate } from 'react-router-dom';
import CargoTicketForm from '../../../features/cargoTickets/components/CargoTicketForm';
import CargoConfirmModal from '../../../features/cargoTickets/components/CargoConfirmModal';
import QrPaymentModal from '../../../features/cargoTickets/components/QrPaymentModal';
import { cargoTicketApi } from '../../../features/cargoTickets/api/cargoTicketApi';
import { formatCurrency } from '../../../utils/formatters';

export default function CargoTicketCreatePage() {
    const navigate = useNavigate();
    const [qrTicket, setQrTicket] = useState(null);
    const [submitError, setSubmitError] = useState(null);
    const [pendingPayload, setPendingPayload] = useState(null);
    const [cashConfirmOpen, setCashConfirmOpen] = useState(false);
    const [abandonConfirmOpen, setAbandonConfirmOpen] = useState(false);
    const [abandonTicketId, setAbandonTicketId] = useState(null);
    const [confirming, setConfirming] = useState(false);

    const goToSend = () => navigate('/staff/cargo-tickets/send');

    const createOrder = async (payload) => {
        setConfirming(true);
        setSubmitError(null);
        try {
            const created = await cargoTicketApi.createCargoTicket(payload);
            const needsSenderBankQr =
                payload.feePayer === 'SENDER'
                && payload.paymentMethod === 'BANK_TRANSFER'
                && created?.payment?.status === 'PENDING';

            if (needsSenderBankQr) {
                setQrTicket(created);
                return;
            }
            goToSend();
        } catch (err) {
            setSubmitError(err.response?.data?.message || 'Không thể tạo đơn gửi hàng.');
            throw err;
        } finally {
            setConfirming(false);
            setCashConfirmOpen(false);
            setPendingPayload(null);
        }
    };

    const handleSubmit = async (payload) => {
        setSubmitError(null);
        if (payload.feePayer === 'SENDER' && payload.paymentMethod === 'CASH') {
            setPendingPayload(payload);
            setCashConfirmOpen(true);
            return;
        }
        await createOrder(payload);
    };

    const handleCashConfirm = async () => {
        if (!pendingPayload) return;
        await createOrder(pendingPayload);
    };

    const handleQrClose = async () => {
        if (!qrTicket) return;
        const ticketId = qrTicket.cargoTicketId;
        setQrTicket(null);
        try {
            const latest = await cargoTicketApi.getCargoTicket(ticketId);
            if (latest?.payment?.status === 'COMPLETED') {
                goToSend();
                return;
            }
            setAbandonTicketId(ticketId);
            setAbandonConfirmOpen(true);
        } catch (err) {
            setSubmitError(err.response?.data?.message || 'Không thể kiểm tra trạng thái thanh toán.');
            goToSend();
        }
    };

    const handleAbandonConfirm = async () => {
        if (!abandonTicketId) return;
        setConfirming(true);
        try {
            await cargoTicketApi.disableCargoTicket(abandonTicketId);
        } catch (err) {
            setSubmitError(err.response?.data?.message || 'Không thể hủy đơn nháp.');
        } finally {
            setConfirming(false);
            setAbandonConfirmOpen(false);
            setAbandonTicketId(null);
            goToSend();
        }
    };

    const handleAbandonCancel = () => {
        setAbandonConfirmOpen(false);
        setAbandonTicketId(null);
        goToSend();
    };

    return (
        <Container fluid className="py-4" style={{ maxWidth: '1200px' }}>
            <Button
                variant="link"
                className="text-decoration-none text-secondary p-0 mb-3 d-flex align-items-center gap-2"
                onClick={goToSend}
            >
                <BsArrowLeft /> Quay lại gửi hàng
            </Button>
            <h2 className="mb-4 fw-bold text-dark">Thêm đơn gửi hàng</h2>
            {submitError && <Alert variant="danger">{submitError}</Alert>}
            <CargoTicketForm
                requireDimensions
                onSubmit={handleSubmit}
            />
            {qrTicket && (
                <QrPaymentModal
                    ticket={qrTicket}
                    onClose={handleQrClose}
                    onSuccess={goToSend}
                />
            )}
            <CargoConfirmModal
                show={cashConfirmOpen}
                title="Xác nhận thu tiền mặt"
                message={
                    pendingPayload
                        ? `Xác nhận đã nhận đủ tiền mặt từ người gửi trước khi tạo đơn?\nSố tiền: ${formatCurrency(pendingPayload.totalPrice || 0)}`
                        : ''
                }
                confirmLabel="Đã nhận tiền"
                confirmVariant="success"
                confirming={confirming}
                onConfirm={handleCashConfirm}
                onCancel={() => {
                    setCashConfirmOpen(false);
                    setPendingPayload(null);
                }}
            />
            <CargoConfirmModal
                show={abandonConfirmOpen}
                title="Hủy đơn nháp"
                message="Thanh toán chuyển khoản chưa hoàn tất. Hủy đơn nháp này?"
                confirmLabel="Hủy đơn"
                confirmVariant="danger"
                confirming={confirming}
                onConfirm={handleAbandonConfirm}
                onCancel={handleAbandonCancel}
            />
        </Container>
    );
}
