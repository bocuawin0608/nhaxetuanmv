import { useCallback, useEffect, useState } from 'react';
import { Alert, Badge, Button, Spinner } from 'react-bootstrap';
import { BsArrowLeft, BsBusFront, BsGeoAltFill, BsPeople, BsTelephone } from 'react-icons/bs';
import { useNavigate } from 'react-router-dom';
import { cargoTicketApi } from '../../../features/cargoTickets/api/cargoTicketApi';
import CargoQueuePanel from '../../../features/cargoTickets/components/CargoQueuePanel';
import Pagination from '../../../components/common/Pagination';
import '../../../features/cargoTickets/styles/CargoOperations.css';

/**
 * Destination workflow that selects an unloaded coach before revealing only
 * that coach's orders.
 */
export default function CargoCheckPage() {
    const navigate = useNavigate();
    const [trips, setTrips] = useState([]);
    const [selectedTrip, setSelectedTrip] = useState(null);
    const [showHistory, setShowHistory] = useState(false);
    const [agency, setAgency] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [pageInfo, setPageInfo] = useState({
        page: 0,
        size: 6,
        totalElements: 0,
        totalPages: 0
    });

    /** Loads only coaches with unloaded orders awaiting this destination office. */
    const loadReceivingTrips = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            const response = await cargoTicketApi.getReceivingTrips({
                page: pageInfo.page,
                size: pageInfo.size
            });
            const tripPage = response.trips ?? {};
            setAgency({
                ticketAgencyName: response.ticketAgencyName,
                stopPointName: response.stopPointName,
                city: response.city
            });
            setTrips(tripPage.content ?? []);
            setPageInfo(previous => ({
                ...previous,
                page: tripPage.pageNumber ?? previous.page,
                totalElements: tripPage.totalElements ?? 0,
                totalPages: tripPage.totalPages ?? 0
            }));
        } catch (requestError) {
            setError(requestError.response?.data?.message || 'Không thể tải danh sách xe đã dỡ hàng.');
        } finally {
            setLoading(false);
        }
    }, [pageInfo.page, pageInfo.size]);

    useEffect(() => {
        // Loading the receiving page intentionally updates local request state.
        // eslint-disable-next-line react-hooks/set-state-in-effect
        loadReceivingTrips();
    }, [loadReceivingTrips]);

    if (selectedTrip) {
        return <main className="cargo-operations-page">
            <div className="cargo-toolbar">
                <Button variant="link" className="cargo-back" onClick={() => setSelectedTrip(null)}>
                    <BsArrowLeft /> Danh sách xe đã dỡ hàng
                </Button>
            </div>
            <header className="cargo-page-heading compact">
                <p className="cargo-eyebrow">Kiểm tra hàng</p>
                <h1>{selectedTrip.licensePlate || 'Chưa gán biển số'}</h1>
                <p>Chỉ hiển thị các đơn cần giao hàng thuộc xe đã chọn.</p>
            </header>
            <CargoQueuePanel
                status="ARRIVED"
                tripId={selectedTrip.tripId}
                confirmable
                onQueueChanged={loadReceivingTrips}
            />
        </main>;
    }

    if (showHistory) {
        return <main className="cargo-operations-page">
            <div className="cargo-toolbar">
                <Button variant="link" className="cargo-back" onClick={() => setShowHistory(false)}>
                    <BsArrowLeft /> Xe đang chờ giao hàng
                </Button>
            </div>
            <header className="cargo-page-heading compact">
                <p className="cargo-eyebrow">Kiểm tra hàng</p>
                <h1>Lịch sử giao hàng</h1>
                <p>Các đơn đã được nhân viên văn phòng đích xác nhận giao hàng.</p>
            </header>
            <CargoQueuePanel status="DELIVERED" />
        </main>;
    }

    return <main className="cargo-operations-page">
        <div className="cargo-toolbar">
            <Button variant="link" className="cargo-back" onClick={() => navigate('/staff/cargo-tickets')}>
                <BsArrowLeft /> Đơn hàng
            </Button>
            <Button className="cargo-primary-button" onClick={() => setShowHistory(true)}>
                Xem lịch sử giao hàng
            </Button>
        </div>
        <header className="cargo-page-heading compact">
            <p className="cargo-eyebrow">Kiểm tra hàng</p>
            <h1>Xe đã dỡ hàng</h1>
            <p>Chọn một xe để xem riêng các đơn đang chờ văn phòng xác nhận giao hàng.</p>
            {agency && <div className="cargo-context-row">
                <div className="cargo-agency-context">
                    <BsGeoAltFill />
                    <span>
                        <small>Văn phòng nhận</small>
                        <strong>{agency.ticketAgencyName}</strong>
                        <em>{agency.stopPointName} · {agency.city}</em>
                    </span>
                </div>
            </div>}
        </header>

        {error && <Alert variant="danger">{error}</Alert>}
        {loading ? <div className="cargo-loading"><Spinner size="sm" /> Đang tải xe đã dỡ hàng...</div> : (
            <section className="cargo-trip-grid">
                {trips.length === 0 && <div className="cargo-empty">Không có xe nào còn đơn hàng chờ giao tại văn phòng này.</div>}
                {trips.map(trip => (
                    <ReceivingTripCard
                        key={trip.tripId}
                        trip={trip}
                        onSelect={() => setSelectedTrip(trip)}
                    />
                ))}
            </section>
        )}
        {!loading && <div className="cargo-pagination">
            <Pagination pageInfo={pageInfo} onPageChange={setPageInfo} />
        </div>}
    </main>;
}

/** Presents one unloaded coach before its order table is requested. */
function ReceivingTripCard({ trip, onSelect }) {
    return (
        <article className="cargo-trip-card">
            <div className="cargo-trip-top">
                <h2>{trip.routeName || 'Chưa có tuyến'}</h2>
                <Badge bg="warning" text="dark">{trip.waitingOrderCount || 0} đơn chờ giao hàng</Badge>
            </div>
            <div className="cargo-coach-line">
                <BsBusFront />
                <strong>{trip.licensePlate || 'Chưa gán biển số'}</strong>
                <span>{trip.coachTypeName || 'Chưa có loại xe'}</span>
                <time>Cập nhật hàng: {formatDateTime(trip.lastCargoUpdateAt)}</time>
            </div>
            <div className="cargo-responsibility-grid">
                <StaffBlock
                    label="Tài xế"
                    name={trip.driverName}
                    phone={trip.driverPhone}
                />
                <StaffBlock
                    label="Phụ xe"
                    name={trip.attendantName}
                    phone={trip.attendantPhone}
                />
            </div>
            <Button className="cargo-primary-button w-100 mt-3" onClick={onSelect}>
                Xem đơn hàng
            </Button>
        </article>
    );
}

/** Displays responsibility details for one assigned trip employee. */
function StaffBlock({ label, name, phone }) {
    return <div className="cargo-staff-block">
        <span><BsPeople /> {label}</span>
        <strong>{name || 'Chưa phân công'}</strong>
        <small><BsTelephone /> {phone || '—'}</small>
    </div>;
}

/** Formats the last unloading update for Vietnamese ticket staff. */
function formatDateTime(value) {
    return value
        ? new Date(value).toLocaleString('vi-VN', {
            hour: '2-digit',
            minute: '2-digit',
            day: '2-digit',
            month: '2-digit',
            year: 'numeric'
        })
        : 'Chưa có thời gian';
}
