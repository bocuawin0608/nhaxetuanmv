import { useState } from 'react';
import { Button, Modal } from 'react-bootstrap';
import { formatCurrency } from '../../../utils/formatters';
import { cargoTicketApi } from '../api/cargoTicketApi';

/**
 * Modal to switch an existing payment's method (CASH ↔ BANK_TRANSFER).
 * Only available when a payment exists but is not yet COMPLETED.
 */
export default function EditPaymentMethodModal({ ticket, onClose, onSuccess }) {
    const currentMethod = ticket?.payment?.paymentMethod;
    const [selected, setSelected] = useState(currentMethod);
    const [busy, setBusy] = useState(false);

    if (!ticket) return null;

    const hasChanged = selected !== currentMethod;

    const handleSave = async () => {
        if (!hasChanged) return;
        setBusy(true);
        try {
            await cargoTicketApi.chooseReceiverPaymentMethod(
                ticket.cargoTicketId, { paymentMethod: selected });
            onSuccess?.();
        } catch (err) {
            window.alert(err.response?.data?.message || 'Không thể đổi hình thức thanh toán.');
        } finally {
            setBusy(false);
        }
    };

    const isCash = selected === 'CASH';

    return (
        <Modal show onHide={() => !busy && onClose?.()} centered backdrop="static">
            <Modal.Header closeButton={!busy}>
                <Modal.Title>Đổi hình thức thanh toán</Modal.Title>
            </Modal.Header>
            <Modal.Body>
                <p className="mb-2">
                    Đơn <strong>{ticket.ticketCode}</strong>
                </p>
                <p className="mb-3 text-muted">
                    Số tiền: {formatCurrency(ticket.totalPrice)}
                </p>

                {/* Toggle switch */}
                <div
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: '12px',
                        userSelect: 'none',
                    }}
                >
                    <span
                        style={{
                            fontWeight: isCash ? 700 : 400,
                            color: isCash ? '#198754' : '#6c757d',
                            transition: 'all .2s',
                            fontSize: '0.95rem',
                        }}
                    >
                        Tiền mặt
                    </span>

                    <button
                        type="button"
                        disabled={busy}
                        aria-label="Chuyển đổi hình thức thanh toán"
                        onClick={() => setSelected(prev =>
                            prev === 'CASH' ? 'BANK_TRANSFER' : 'CASH'
                        )}
                        style={{
                            position: 'relative',
                            width: '52px',
                            height: '28px',
                            borderRadius: '14px',
                            border: 'none',
                            cursor: busy ? 'not-allowed' : 'pointer',
                            background: isCash ? '#198754' : '#0d6efd',
                            transition: 'background .25s',
                            padding: 0,
                            flexShrink: 0,
                        }}
                    >
                        <span
                            style={{
                                position: 'absolute',
                                top: '3px',
                                left: isCash ? '3px' : '25px',
                                width: '22px',
                                height: '22px',
                                borderRadius: '50%',
                                background: '#fff',
                                transition: 'left .25s',
                                boxShadow: '0 1px 3px rgba(0,0,0,.2)',
                            }}
                        />
                    </button>

                    <span
                        style={{
                            fontWeight: !isCash ? 700 : 400,
                            color: !isCash ? '#0d6efd' : '#6c757d',
                            transition: 'all .2s',
                            fontSize: '0.95rem',
                        }}
                    >
                        Chuyển khoản
                    </span>
                </div>
            </Modal.Body>
            <Modal.Footer className="d-flex justify-content-end gap-2">
                <Button variant="secondary" onClick={onClose} disabled={busy}>
                    Đóng
                </Button>
                <Button
                    variant="primary"
                    disabled={busy || !hasChanged}
                    onClick={handleSave}
                >
                    {busy ? 'Đang xử lý...' : 'Lưu thay đổi'}
                </Button>
            </Modal.Footer>
        </Modal>
    );
}
