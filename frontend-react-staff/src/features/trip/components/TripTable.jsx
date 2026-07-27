import {
    BsBoxSeam, BsBusFront, BsClock, BsExclamationCircle,
    BsPencil, BsPeople, BsPersonBadge, BsTrash
} from 'react-icons/bs';
import './TripTable.css';

const STATUS_META = {
    DRAFT: ['Bản nháp', 'draft'], SCHEDULED: ['Đã lên lịch', 'scheduled'],
    OPEN_FOR_SALE: ['Đang mở bán', 'sale'], OPENFORSALE: ['Đang mở bán', 'sale'],
    CLOSED_FOR_SALE: ['Đã đóng bán', 'neutral'], CLOSEDFORSALE: ['Đã đóng bán', 'neutral'],
    BOARDING: ['Đang đón khách', 'boarding'], DELAYED: ['Bị trễ', 'warning'],
    DEPARTED: ['Đã khởi hành', 'active'], IN_PROGRESS: ['Đang di chuyển', 'active'],
    INPROGRESS: ['Đang di chuyển', 'active'], ARRIVED: ['Đã đến', 'arrived'],
    COMPLETED: ['Hoàn tất', 'completed'], CANCELLED: ['Đã hủy', 'cancelled'],
    CANCELED: ['Đã hủy', 'cancelled']
};

const normalizeStatus = (status) => String(status || '').trim().toUpperCase();

const getLoad = (used, capacity) => {
    const safeUsed = Number(used) || 0;
    const safeCapacity = Number(capacity) || 2.5;
    return { used: safeUsed, capacity: safeCapacity, percent: Math.min(100, Math.round((safeUsed / safeCapacity) * 100)) };
};

const getSeatLoad = (available, total) => {
    const safeTotal = Number(total) || 0;
    const safeAvailable = Number(available) || 0;
    const occupied = Math.max(0, safeTotal - safeAvailable);
    return { occupied, total: safeTotal, available: safeAvailable, percent: safeTotal ? Math.round((occupied / safeTotal) * 100) : 0 };
};

export default function TripTable({ data, loading, onViewCrew, onEditInfo, onDelete }) {
    if (loading) {
        return <div className="trip-card-grid" aria-label="Đang tải chuyến xe">{[1, 2, 3, 4, 5, 6].map(item => <div key={item} className="trip-card trip-card--skeleton" />)}</div>;
    }

    if (!data?.length) {
        return <div className="trip-empty-state"><BsBusFront /><h3>Không có chuyến phù hợp</h3><p>Thử đổi ngày hoặc bỏ bớt bộ lọc để xem thêm chuyến.</p></div>;
    }

    return (
        <section className="trip-card-grid" aria-label="Danh sách chuyến xe">
            {data.map((trip) => {
                const status = normalizeStatus(trip.tripStatus);
                const hasIncident = trip.coachStatus === 'HAVE_INCIDENT';
                const [statusLabel, statusTone] = hasIncident
                    ? ['XE GẶP SỰ CỐ', 'incident']
                    : (STATUS_META[status] || [trip.tripStatus || 'Chưa xác định', 'neutral']);
                const seats = getSeatLoad(trip.availableSeats, trip.totalSeats);
                const cargo = getLoad(trip.usedCargoVolume, trip.cargoCapacity);
                const missingCrew = !trip.driverName || !trip.attendantName;
                const canManage = status === 'SCHEDULED';

                return (
                    <article key={trip.tripId} className={`trip-card trip-card--${statusTone}`}>
                        <button type="button" className="trip-card-body" onClick={() => onViewCrew(trip)}>
                            <div className="trip-card-top">
                                <div>
                                    <small>Chuyến #{trip.tripId}</small>
                                    <h2>{String(trip.routeName || 'Chưa có tuyến').replace(/\s*-\s*/, ' → ')}</h2>
                                </div>
                                <span className={`trip-status trip-status--${statusTone}`}><i />{statusLabel}</span>
                            </div>

                            <div className="trip-card-schedule">
                                <strong><BsClock /> {String(trip.departureTime || '').substring(0, 5) || '—'}</strong>
                                <span><BsBusFront /> <b>{trip.licensePlate || 'Chưa gán xe'}</b></span>
                                <small>{[trip.manufacturer, trip.coachTypeName].filter(Boolean).join(' · ') || 'Chưa có thông tin xe'}</small>
                            </div>

                            {hasIncident && (
                                <div className="trip-card-emergency">
                                    <BsExclamationCircle />
                                    <strong>KHẨN CẤP: XE {trip.licensePlate} GẶP SỰ CỐ KHÔNG THỂ KHẮC PHỤC!!!</strong>
                                </div>
                            )}

                            <div className="trip-load-grid">
                                <LoadBlock icon={<BsPeople />} label="Hành khách" value={`${seats.occupied}/${seats.total}`} detail={`${seats.available} ghế trống`} percent={seats.percent} />
                                <LoadBlock icon={<BsBoxSeam />} label="Khoang hàng" value={`${cargo.percent}%`} detail={`${cargo.used.toFixed(2)}/${cargo.capacity.toFixed(2)} m³`} percent={cargo.percent} critical={cargo.percent >= 90} />
                            </div>

                            <div className={`trip-crew-line${missingCrew ? ' is-warning' : ''}`}>
                                {missingCrew ? <BsExclamationCircle /> : <BsPersonBadge />}
                                <span><small>Tổ lái</small><strong>{trip.driverName || 'Thiếu tài xế'} · {trip.attendantName || 'Thiếu phụ xe'}</strong></span>
                            </div>
                        </button>

                        <div className="trip-card-actions" aria-label={`Thao tác chuyến ${trip.tripId}`}>
                            <button
                                type="button"
                                onClick={() => onEditInfo(trip)}
                                disabled={!canManage}
                                title={canManage ? undefined : 'Chuyến đã bắt đầu hoặc kết thúc, chỉ có thể xem'}
                            >
                                <BsPencil /> {hasIncident ? 'Điều xe thay thế' : 'Chỉnh sửa'}
                            </button>
                            {canManage && <button type="button" className="is-danger" onClick={() => onDelete(trip)}><BsTrash /> Hủy chuyến</button>}
                        </div>
                    </article>
                );
            })}
        </section>
    );
}

function LoadBlock({ icon, label, value, detail, percent, critical = false }) {
    return (
        <div className="trip-load-block">
            <div><span>{icon} {label}</span><strong>{value}</strong></div>
            <div className={`trip-progress${critical ? ' is-critical' : ''}`}><i style={{ width: `${percent}%` }} /></div>
            <small>{detail}</small>
        </div>
    );
}
