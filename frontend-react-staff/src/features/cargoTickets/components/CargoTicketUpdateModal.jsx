import { useEffect, useState } from 'react';
import { Modal, Spinner } from 'react-bootstrap';
import CargoTicketForm from './CargoTicketForm';
import { cargoTicketApi } from '../api/cargoTicketApi';

export default function CargoTicketUpdateModal({ data, onClose, onSuccess }) {
    const [ticket, setTicket] = useState(null);

    useEffect(() => {
        if (!data?.cargoTicketId) return;
        let cancelled = false;
        Promise.all([
            cargoTicketApi.getCargoTicket(data.cargoTicketId),
            cargoTicketApi.getCargoTicketDetails(data.cargoTicketId).catch(() => [])
        ])
            .then(([full, details]) => {
                if (cancelled) return;
                // Prefer full GET (includes nested payment); fall back to queue row payment.
                setTicket({
                    ...full,
                    details,
                    payment: full?.payment ?? data.payment ?? null
                });
            })
            .catch(() => {
                if (!cancelled) {
                    setTicket({ ...data, details: [], payment: data.payment ?? null });
                }
            });
        return () => { cancelled = true; };
    }, [data]);

    if (!data) return null;

    const handleSubmit = async (payload) => {
        await cargoTicketApi.updateCargoTicketWithDetails(data.cargoTicketId, payload);
        await onSuccess();
        onClose();
    };

    return (
        <Modal show onHide={onClose} size="xl" centered backdrop="static">
            <Modal.Header closeButton><Modal.Title>Cập nhật đơn gửi hàng</Modal.Title></Modal.Header>
            <Modal.Body className="p-4">{ticket === null
                ? <div className="text-center p-5"><Spinner size="sm" /> Đang tải thông tin hiện tại...</div>
                : (
                    <CargoTicketForm
                        key={ticket.cargoTicketId}
                        initialData={ticket}
                        onSubmit={handleSubmit}
                        submitLabel="Lưu thay đổi"
                    />
                )}
            </Modal.Body>
        </Modal>
    );
}
