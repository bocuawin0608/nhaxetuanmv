import { Button, Modal } from 'react-bootstrap';

/**
 * In-app confirmation dialog. Prefer this over window.confirm — native dialogs
 * are often blocked or invisible in embedded staff browsers.
 */
export default function CargoConfirmModal({
    show,
    title,
    message,
    confirmLabel = 'Xác nhận',
    cancelLabel = 'Hủy',
    confirmVariant = 'primary',
    confirming = false,
    onConfirm,
    onCancel,
}) {
    return (
        <Modal show={show} onHide={onCancel} centered backdrop="static">
            <Modal.Header closeButton={!confirming}>
                <Modal.Title>{title}</Modal.Title>
            </Modal.Header>
            <Modal.Body>
                {typeof message === 'string'
                    ? message.split('\n').map((line, index) => (
                        <p key={index} className={index === 0 ? 'mb-2' : 'mb-0 text-muted'}>{line}</p>
                    ))
                    : message}
            </Modal.Body>
            <Modal.Footer>
                <Button className="custom-btn-general" onClick={onCancel} disabled={confirming}>
                    {cancelLabel}
                </Button>
                <Button
                    variant={confirmVariant}
                    className={confirmVariant === 'primary' ? 'custom-btn-general' : undefined}
                    onClick={onConfirm}
                    disabled={confirming}
                >
                    {confirming ? 'Đang xử lý...' : confirmLabel}
                </Button>
            </Modal.Footer>
        </Modal>
    );
}
