import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { BsArrowLeft, BsCheckCircle, BsExclamationTriangleFill } from 'react-icons/bs';
import { routeApi } from '../../../features/routes/api/routeApi';
import { coachStopApi } from '../../../features/coachStops/api/coachStopApi';
import { Alert, Button, Card, Col, Container, Form, Row } from 'react-bootstrap';
import RouteStopsDndManager from '../../../features/routes/components/RouteStopsDndManager';

export default function RouteCreatePage() {
    const navigate = useNavigate();

    const [formData, setFormData] = useState({
        routeName: '',
        active: true
    });

    const [isSubmitting, setIsSubmitting] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');

    // DND States
    const [available, setAvailable] = useState([]);
    const [selected, setSelected] = useState([]);

    useEffect(() => {
        const fetchStops = async () => {
            try {
                const res = await coachStopApi.getAllCoachStops('', true, 0, 1000);
                setAvailable(res.content || []);
            } catch (err) {
                console.error("Lỗi khi tải danh sách trạm dừng:", err);
            }
        };
        fetchStops();
    }, []);

    const handleInputChange = (e) => {
        const { name, value, type, checked } = e.target;
        setFormData(prev => ({
            ...prev,
            [name]: type === 'checkbox' ? checked : value
        }));
    };

    // --- Submit ---
    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrorMsg('');

        if (selected.length < 2) {
            setErrorMsg('Tuyến đường phải có ít nhất 2 trạm dừng trong danh sách chính.');
            return;
        }

        setIsSubmitting(true);

        try {
            const payload = {
                routeName: formData.routeName,
                totalKilometers: 0,
                totalMinutes: 0,
                active: formData.active,
                routeStops: selected.map((s, index) => ({
                    stopPointId: s.stopPointId,
                    stopOrder: index + 1, // dynamically mapped based on array order!
                    kilometersFromStart: 0,
                    minutesFromStart: 0
                }))
            };

            await routeApi.createRouteWithStops(payload);

            navigate('/management/routes');
        } catch (error) {
            console.error("Lỗi tạo tuyến xe:", error);
            setErrorMsg(error.response?.data?.message || 'Có lỗi xảy ra khi lưu vào hệ thống.');
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <Container fluid className="py-4" style={{ maxWidth: '1200px' }}>
            <Button
                variant="link"
                onClick={() => navigate('/management/routes')}
                className="text-decoration-none text-secondary p-0 mb-3 d-flex align-items-center gap-2 fw-medium"
            >
                <BsArrowLeft size={18} /> Quay lại danh sách
            </Button>

            <h2 className="mb-4 text-dark fw-bold">Thêm mới tuyến xe</h2>

            {errorMsg && (
                <Alert variant="danger" className="shadow-sm border-0 d-flex align-items-center gap-2">
                    <BsExclamationTriangleFill />
                    <span>{errorMsg}</span>
                </Alert>
            )}

            <Form onSubmit={handleSubmit}>
                <Card className="shadow-sm border-0 mb-4">
                    <Card.Header className="bg-white border-bottom pt-3 pb-2">
                        <h5 className="fw-bold mb-0 text-dark">Thông tin cơ bản</h5>
                    </Card.Header>
                    <Card.Body className="p-4 d-flex flex-column gap-3">
                        <Row className="g-3">
                            <Col md={8}>
                                <Form.Group>
                                    <Form.Label className="fw-semibold text-secondary mb-1">Tên tuyến đường <span className="text-danger">*</span></Form.Label>
                                    <Form.Control
                                        type="text"
                                        name="routeName"
                                        required
                                        maxLength={100}
                                        placeholder="Ví dụ: Hà Nội - Quảng Trị"
                                        value={formData.routeName}
                                        onChange={handleInputChange}
                                        className="py-2"
                                    />
                                    <small className="text-muted">Định dạng: "Điểm Đầu - Điểm Cuối"</small>
                                </Form.Group>
                            </Col>
                            <Col md={4}>
                                <Form.Group>
                                    <Form.Label className="fw-semibold text-secondary mb-1">Trạng thái ban đầu</Form.Label>
                                    <div className="d-flex align-items-center justify-content-between p-2 bg-light border rounded h-100">
                                        <Form.Check
                                            type="switch"
                                            id="active-switch"
                                            name="active"
                                            label="Kích hoạt ngay"
                                            checked={formData.active}
                                            onChange={handleInputChange}
                                            className="fw-medium text-dark m-0"
                                        />
                                    </div>
                                </Form.Group>
                            </Col>
                        </Row>
                    </Card.Body>
                </Card>

                <Card className="shadow-sm border-0 mb-4">
                    <Card.Header className="bg-white border-bottom pt-3 pb-2">
                        <h5 className="fw-bold mb-0 text-dark">Danh sách trạm dừng</h5>
                        <small className="text-muted">
                            Kéo thả để thêm/xóa trạm. Kéo lên/xuống để sắp xếp thứ tự lộ trình.
                        </small>
                    </Card.Header>
                    <Card.Body className="p-4 bg-light">
                        <RouteStopsDndManager
                            available={available}
                            setAvailable={setAvailable}
                            selected={selected}
                            setSelected={setSelected}
                        />
                    </Card.Body>
                </Card>

                <Button
                    type="submit"
                    disabled={isSubmitting || selected.length < 2}
                    className="w-100 py-3 mb-5 fw-bold d-flex justify-content-center align-items-center gap-2 fs-5 custom-btn-general"
                >
                    <BsCheckCircle size={22} />
                    {isSubmitting ? 'Đang tạo tuyến đường...' : 'Hoàn Tất Tuyến Đường'}
                </Button>
            </Form>
        </Container>
    );
}
