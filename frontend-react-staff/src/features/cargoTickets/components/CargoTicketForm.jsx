import { Alert, Button, Card, Col, Form, Row } from 'react-bootstrap';
import { BsCheckCircle, BsExclamationTriangleFill, BsGeoAltFill } from 'react-icons/bs';
import { useMemo, useState, useEffect } from 'react';
import { useCargoTicketFormOptions } from '../hooks/useCargoTicketFormOptions';
import { useAuth } from '../../auth/context/AuthContext';
import { routeApi } from '../../routes/api/routeApi';
import { formatCurrency, safeRound } from '../../../utils/formatters';
import PhoneAutocomplete from './PhoneAutocomplete';
import CargoTicketDetailSection from './CargoTicketDetailSection';
import '../styles/CargoTicketForm.css';

const EMPTY_FORM = {
    tripId: '', customerId: '', senderName: '', senderPhone: '',
    receiverName: '', receiverPhone: '',
    totalPrice: '', description: '', feePayer: 'SENDER', codAmount: 0,
    pickupStopId: '', dropoffStopId: '', status: 'RECEIVED', soldBy: '',
    paymentMethod: 'CASH'
};

const MAX_CARGO_VOLUME_M3 = 2.5;

/** Prefers nested payment.paymentMethod from API ticket responses. */
function resolveInitialPaymentMethod(initialData) {
    const feePayer = initialData?.feePayer ?? EMPTY_FORM.feePayer;
    if (feePayer === 'RECEIVER') return '';
    return initialData?.payment?.paymentMethod
        ?? initialData?.paymentMethod
        ?? EMPTY_FORM.paymentMethod;
}

/**
 * Ticket-staff cargo form.
 * Pickup is always the authenticated agency stop; staff only choose dropoff.
 * Trip assignment is deferred on create; Eligible Trip appears on update only.
 */
export default function CargoTicketForm({
    initialData,
    lockedTrip,
    requireDimensions = false,
    onSubmit,
    submitLabel = 'Lưu đơn gửi hàng'
}) {
    const [formData, setFormData] = useState(() => ({
        ...EMPTY_FORM,
        ...initialData,
        soldBy: initialData?.soldBy?.staffId ?? initialData?.soldBy ?? EMPTY_FORM.soldBy,
        tripId: initialData?.tripId ?? EMPTY_FORM.tripId,
        pickupStopId: initialData?.pickupStopId ?? lockedTrip?.pickupStopId ?? EMPTY_FORM.pickupStopId,
        dropoffStopId: initialData?.dropoffStopId ?? EMPTY_FORM.dropoffStopId,
        feePayer: initialData?.feePayer ?? EMPTY_FORM.feePayer,
        paymentMethod: resolveInitialPaymentMethod(initialData),
        codAmount: initialData?.codAmount ?? EMPTY_FORM.codAmount,
        description: initialData?.description ?? EMPTY_FORM.description
    }));
    const [draftDetails, setDraftDetails] = useState(() =>
        initialData?.details ? structuredClone(initialData.details) : []
    );
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');
    const { user } = useAuth();
    const isCreate = requireDimensions;
    const {
        trips,
        stops,
        sellers,
        loading: optionsLoading,
        error: optionsError,
        agencyPickupStopId,
        agencyPickupStopName,
        agencyCity,
        defaultRouteId,
        defaultRouteName
    } = useCargoTicketFormOptions(formData.pickupStopId, formData.dropoffStopId, {
        loadTrips: !isCreate
    });

    const [selectedRouteId, setSelectedRouteId] = useState(() => {
        if (lockedTrip?.routeId) return String(lockedTrip.routeId);
        if (initialData?.routeId) return String(initialData.routeId);
        return '';
    });
    const [routeDetail, setRouteDetail] = useState(null);
    const [routeLabel, setRouteLabel] = useState(
        () => lockedTrip?.routeName || initialData?.routeName || ''
    );
    const hasLockedTrip = Boolean(lockedTrip) && !isCreate;
    const isPaid = initialData?.payment?.status === 'COMPLETED';
    const moneyFieldsLocked = isPaid;

    const lockedPickupStopId = lockedTrip?.pickupStopId
        || agencyPickupStopId
        || initialData?.pickupStopId
        || '';
    const lockedPickupName = lockedTrip?.pickupStopName
        || agencyPickupStopName
        || initialData?.pickupStopName
        || '';
    const lockedPickupCity = lockedTrip?.pickupCity || agencyCity || '';

    // Lock pickup to agency / trip office as soon as context is known.
    useEffect(() => {
        if (!lockedPickupStopId) return;
        setFormData((previous) => {
            if (String(previous.pickupStopId) === String(lockedPickupStopId)) return previous;
            return { ...previous, pickupStopId: lockedPickupStopId, tripId: previous.tripId && !isCreate ? previous.tripId : '' };
        });
    }, [lockedPickupStopId, isCreate]);

    // Auto-select outbound route for create; keep update route from ticket/trip.
    useEffect(() => {
        if (hasLockedTrip && lockedTrip?.routeId) {
            setSelectedRouteId(String(lockedTrip.routeId));
            if (lockedTrip.routeName) setRouteLabel(lockedTrip.routeName);
            return;
        }
        if (selectedRouteId) return;
        if (initialData?.routeId) {
            setSelectedRouteId(String(initialData.routeId));
            if (initialData.routeName) setRouteLabel(initialData.routeName);
            return;
        }
        if (defaultRouteId) {
            setSelectedRouteId(String(defaultRouteId));
            if (defaultRouteName) setRouteLabel(defaultRouteName);
        }
    }, [hasLockedTrip, lockedTrip, selectedRouteId, initialData, defaultRouteId, defaultRouteName]);

    useEffect(() => {
        if (!selectedRouteId) {
            setRouteDetail(null);
            return;
        }
        routeApi.getRouteDetail(selectedRouteId)
            .then((route) => {
                setRouteDetail(route);
                if (route?.routeName) setRouteLabel(route.routeName);
            })
            .catch(() => setRouteDetail(null));
    }, [selectedRouteId]);

    useEffect(() => {
        if (sellers?.length > 0 && user?.username && !formData.soldBy && !initialData?.cargoTicketId) {
            const match = sellers.find(s => s.username === user.username);
            if (match) {
                // eslint-disable-next-line react-hooks/set-state-in-effect
                setFormData(prev => ({ ...prev, soldBy: match.staffId }));
            }
        }
    }, [sellers, user, formData.soldBy, initialData]);

    const eligibleTrips = useMemo(
        () => filterTripsForSelectedStops(trips, stops, formData.pickupStopId, formData.dropoffStopId),
        [trips, stops, formData.pickupStopId, formData.dropoffStopId]
    );

    // Keep the current trip visible even when it drops out of the eligible list
    // (departed / filtered). Staff must clear or change it explicitly.
    const tripSelectOptions = useMemo(() => {
        if (!formData.tripId) return eligibleTrips;
        const stillListed = eligibleTrips.some(
            (trip) => String(trip.tripId) === String(formData.tripId)
        );
        if (stillListed) return eligibleTrips;
        return [
            {
                tripId: formData.tripId,
                departureTime: null,
                status: null,
                coachTypeName: null,
                staleCurrent: true
            },
            ...eligibleTrips
        ];
    }, [eligibleTrips, formData.tripId]);

    const stopsForRoute = useMemo(() => {
        if (!selectedRouteId || !routeDetail?.routeStops?.length) return [];
        return routeDetail.routeStops.map(rs => ({
            stopPointId: rs.stopPointId,
            stopPointName: rs.stopPointName,
            city: rs.city,
            stopOrder: rs.stopOrder,
        })).sort((a, b) => a.stopOrder - b.stopOrder);
    }, [selectedRouteId, routeDetail]);

    // Cách 2: chỉ điểm trả ở city đầu kia + có văn phòng vé (ẩn cùng city điểm nhận / điểm giữa).
    const agencyStopIds = useMemo(
        () => new Set((stops || []).map((stop) => String(stop.stopPointId))),
        [stops]
    );

    const dropoffStopOptions = useMemo(() => {
        if (!formData.pickupStopId || !stopsForRoute.length) return [];
        const pickup = stopsForRoute.find(
            (stop) => String(stop.stopPointId) === String(formData.pickupStopId)
        );
        if (!pickup?.city) return [];
        const pickupCity = String(pickup.city).trim().toLowerCase();
        return stopsForRoute.filter((stop) => {
            if (stop.stopOrder <= pickup.stopOrder) return false;
            if (agencyStopIds.size > 0 && !agencyStopIds.has(String(stop.stopPointId))) return false;
            const stopCity = String(stop.city ?? '').trim().toLowerCase();
            return stopCity && stopCity !== pickupCity;
        });
    }, [stopsForRoute, formData.pickupStopId, agencyStopIds]);

    // Clear a previously selected mid-route / same-city dropoff once options load.
    useEffect(() => {
        if (!formData.dropoffStopId || dropoffStopOptions.length === 0) return;
        const stillValid = dropoffStopOptions.some(
            (stop) => String(stop.stopPointId) === String(formData.dropoffStopId)
        );
        if (!stillValid) {
            setFormData((previous) => ({
                ...previous,
                dropoffStopId: '',
                ...(hasLockedTrip ? {} : { tripId: '' })
            }));
        }
    }, [dropoffStopOptions, formData.dropoffStopId, hasLockedTrip]);

    const isFormComplete = useMemo(
        () => isCargoTicketFormComplete(formData, draftDetails, requireDimensions),
        [formData, draftDetails, requireDimensions]
    );
    const occupiedVolume = useMemo(() => calculateOccupiedVolume(draftDetails), [draftDetails]);
    const estimatedTotal = useMemo(() => sumCalculatedPrices(draftDetails), [draftDetails]);
    const pricesReady = useMemo(
        () => draftDetails.length > 0 && draftDetails.every((d) => Number(d.calculatedPrice) > 0),
        [draftDetails]
    );

    const handleChange = (event) => {
        const { name, value } = event.target;
        if (moneyFieldsLocked && (name === 'feePayer' || name === 'paymentMethod')) {
            return;
        }
        setFormData((previous) => ({
            ...previous,
            [name]: value,
            ...(name === 'dropoffStopId' && !hasLockedTrip ? { tripId: '' } : {}),
            ...(name === 'feePayer' && value === 'RECEIVER' ? { paymentMethod: '' } : {}),
            ...(name === 'feePayer' && value === 'SENDER' && !previous.paymentMethod
                ? { paymentMethod: 'CASH' }
                : {})
        }));
        setError('');
    };

    const handleAddDetail = () => {
        setDraftDetails(prev => {
            const newDetails = structuredClone(prev);
            newDetails.push({
                cargoTypePriceId: '', description: '', quantity: 1, weightKg: '',
                length: '', width: '', height: '', dimensionVol: ''
            });
            return newDetails;
        });
    };

    const handleDetailChange = (index, field, value) => {
        setDraftDetails(prev => {
            const newDetails = structuredClone(prev);
            newDetails[index][field] = value;
            return newDetails;
        });
    };

    const handleRemoveDetail = (index) => {
        setDraftDetails(prev => {
            const newDetails = structuredClone(prev);
            newDetails.splice(index, 1);
            return newDetails;
        });
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        if (occupiedVolume > MAX_CARGO_VOLUME_M3) {
            setError('Tổng thể tích hàng hóa (thể tích × số lượng) không được vượt quá 2,5 m³.');
            return;
        }
        if (!isFormComplete) {
            setError('Vui lòng điền đầy đủ và đúng tất cả thông tin bắt buộc.');
            return;
        }
        if (String(formData.pickupStopId) === String(formData.dropoffStopId)) {
            setError('Điểm nhận và điểm trả hàng phải khác nhau.');
            return;
        }
        if (!initialData?.cargoTicketId && draftDetails.length === 0) {
            setError('Vui lòng thêm ít nhất một chi tiết hàng hóa.');
            return;
        }

        const payload = buildCargoTicketRequest({
            ...formData,
            // Create always defers trip assignment (UC-19 / Assign board).
            tripId: isCreate ? '' : formData.tripId,
            // Paid orders must echo the collected payment method (never the form default).
            paymentMethod: moneyFieldsLocked
                ? (initialData?.payment?.paymentMethod ?? formData.paymentMethod)
                : formData.paymentMethod,
            feePayer: moneyFieldsLocked
                ? (initialData?.feePayer ?? formData.feePayer)
                : formData.feePayer,
            pickupStopId: lockedPickupStopId || formData.pickupStopId
        }, draftDetails);
        setSubmitting(true);
        try {
            await onSubmit(payload);
        } catch (err) {
            const response = err.response?.data;
            const validationDetails = response?.fieldErrors
                ? Object.values(response.fieldErrors).join(' ')
                : '';
            setError(validationDetails || response?.message || 'Không thể lưu đơn gửi hàng.');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Form id="cargo-ticket-form" onSubmit={handleSubmit}>
            {(error || optionsError) && (
                <Alert variant="danger" className="d-flex align-items-center gap-2">
                    <BsExclamationTriangleFill />{error || optionsError}
                </Alert>
            )}

            <Row className="g-4 mb-4">
                <Col lg={6}><PartyCard title="Người gửi" prefix="sender" data={formData} onChange={handleChange} setFormData={setFormData} /></Col>
                <Col lg={6}><PartyCard title="Người nhận" prefix="receiver" data={formData} onChange={handleChange} setFormData={setFormData} /></Col>
            </Row>

            <Card className="shadow-sm border-0 mb-4">
                <Card.Header className="bg-white py-3">
                    <h5 className="fw-bold mb-0">Thông tin vé và hành trình</h5>
                </Card.Header>
                <Card.Body className="p-4">
                    <Row className="g-3">
                        <Col md={6}>
                            <PickupSummary
                                stopName={lockedPickupName}
                                city={lockedPickupCity}
                                routeName={routeLabel || defaultRouteName}
                            />
                        </Col>
                        <Col md={6}>
                            <Dropdown
                                label="Điểm trả"
                                name="dropoffStopId"
                                value={formData.dropoffStopId ?? ''}
                                onChange={handleChange}
                                loading={optionsLoading || Boolean(selectedRouteId && !routeDetail)}
                                options={dropoffStopOptions}
                                optionValue="stopPointId"
                                renderOption={(item) => `${item.stopPointName} (${item.city})`}
                                required
                                disabled={!selectedRouteId || !formData.pickupStopId}
                                emptyLabel={!selectedRouteId ? '-- Đang chọn tuyến --' : '-- Chọn điểm trả --'}
                            />
                        </Col>
                        {!isCreate && !hasLockedTrip && (
                            <Col md={12}>
                                <Dropdown
                                    label="Chuyến đi phù hợp (có thể gán sau)"
                                    name="tripId"
                                    value={formData.tripId ?? ''}
                                    onChange={handleChange}
                                    loading={optionsLoading}
                                    options={tripSelectOptions}
                                    optionValue="tripId"
                                    renderOption={tripLabel}
                                    disabled={!formData.pickupStopId || !formData.dropoffStopId}
                                    emptyLabel="-- Gán chuyến sau --"
                                />
                                {!isPaid && formData.feePayer === 'SENDER' && (
                                    <Form.Text className="text-muted">
                                        Gán hoặc đổi chuyến chỉ khi người gửi đã thanh toán xong
                                        (hoặc dùng màn Gán hàng sau khi trả).
                                    </Form.Text>
                                )}
                            </Col>
                        )}
                        {!isCreate && hasLockedTrip && (
                            <Col md={12}>
                                <CoachSummary lockedTrip={lockedTrip} />
                            </Col>
                        )}
                        {isCreate && (
                            <Col xs={12}>
                                <Form.Text className="text-muted">
                                    Đơn mới luôn chưa gán chuyến. Gán xe sau ở màn Gửi hàng → Xem chuyến xe / Gán hàng,
                                    hoặc khi cập nhật đơn đang chờ.
                                </Form.Text>
                            </Col>
                        )}
                    </Row>
                </Card.Body>
            </Card>

            <Card className="shadow-sm border-0 mb-4">
                <Card.Header className="bg-white py-3">
                    <h5 className="fw-bold mb-0">Thanh toán và xử lý</h5>
                </Card.Header>
                <Card.Body className="p-4">
                    <Row className="g-3">
                        <Col md={4}>
                            <Field
                                label="Tiền thu hộ COD (VNĐ)"
                                name="codAmount"
                                type="number"
                                value={formData.codAmount ?? 0}
                                onChange={handleChange}
                                required
                                min="0"
                            />
                        </Col>
                        <Col md={4}>
                            <Form.Group>
                                <Form.Label className="fw-semibold">Người trả phí *</Form.Label>
                                <Form.Select
                                    name="feePayer"
                                    value={formData.feePayer}
                                    onChange={handleChange}
                                    disabled={moneyFieldsLocked}
                                >
                                    <option value="SENDER">Người gửi</option>
                                    <option value="RECEIVER">Người nhận</option>
                                </Form.Select>
                            </Form.Group>
                        </Col>
                        <Col md={4}>
                            <Form.Group>
                                <Form.Label className="fw-semibold">
                                    Phương thức thanh toán{formData.feePayer === 'SENDER' ? ' *' : ''}
                                </Form.Label>
                                <Form.Select
                                    name="paymentMethod"
                                    value={formData.feePayer === 'RECEIVER' ? '' : formData.paymentMethod}
                                    onChange={handleChange}
                                    required={formData.feePayer === 'SENDER'}
                                    disabled={moneyFieldsLocked || formData.feePayer === 'RECEIVER'}
                                >
                                    {formData.feePayer === 'RECEIVER' ? (
                                        <option value="">Chọn lúc nhận hàng ở văn phòng đích</option>
                                    ) : (
                                        <>
                                            <option value="CASH">Tiền mặt</option>
                                            <option value="BANK_TRANSFER">Chuyển khoản</option>
                                        </>
                                    )}
                                </Form.Select>
                                {formData.feePayer === 'RECEIVER' && !moneyFieldsLocked && (
                                    <Form.Text className="text-muted">
                                        Người nhận sẽ chọn tiền mặt hoặc chuyển khoản khi lấy hàng.
                                    </Form.Text>
                                )}
                            </Form.Group>
                        </Col>
                        {moneyFieldsLocked && (
                            <Col xs={12}>
                                <Alert variant="warning" className="mb-0 py-2 small">
                                    Đơn đã thanh toán: người trả phí, phương thức và chi tiết hàng hóa (ảnh hưởng cước)
                                    bị khóa. Muốn đổi tiền cước/hàng, hãy hủy đơn và tạo đơn mới. Vẫn có thể sửa COD,
                                    liên hệ, điểm trả, chuyến và mô tả đơn.
                                </Alert>
                            </Col>
                        )}
                        <Col xs={12}>
                            <Form.Group>
                                <Form.Label className="fw-semibold">Mô tả đơn hàng</Form.Label>
                                <Form.Control
                                    as="textarea"
                                    rows={3}
                                    name="description"
                                    value={formData.description || ''}
                                    onChange={handleChange}
                                />
                            </Form.Group>
                        </Col>
                    </Row>
                </Card.Body>
            </Card>

            <CargoTicketDetailSection
                draftDetails={draftDetails}
                onAdd={handleAddDetail}
                onChange={handleDetailChange}
                onRemove={handleRemoveDetail}
                readOnly={moneyFieldsLocked}
            />

            {occupiedVolume > MAX_CARGO_VOLUME_M3 && (
                <Alert variant="danger" className="cargo-volume-limit-alert">
                    Tổng thể tích hiện tại là {formatVolume(occupiedVolume)} m³, vượt giới hạn 2,5 m³.
                </Alert>
            )}

            <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
                <div className="fs-5 mb-0">
                    Tổng cước:{' '}
                    <strong className="text-success">
                        {pricesReady ? formatCurrency(estimatedTotal) : 'Đang tính...'}
                    </strong>
                </div>
                <Button
                    type="submit"
                    disabled={!isFormComplete || !pricesReady || submitting || optionsLoading || Boolean(optionsError)}
                    className="px-4 py-2 d-flex align-items-center gap-2 custom-btn-general"
                >
                    <BsCheckCircle />{submitting ? 'Đang lưu...' : submitLabel}
                </Button>
            </div>
        </Form>
    );
}

function PickupSummary({ stopName, city, routeName }) {
    return (
        <div className="cargo-form-coach-summary" aria-label="Điểm nhận tại văn phòng">
            <span><BsGeoAltFill /> Điểm nhận (văn phòng hiện tại)</span>
            <strong>{stopName || 'Đang tải...'}</strong>
            <small>
                {[city, routeName ? `Tuyến: ${routeName}` : null].filter(Boolean).join(' · ') || 'Tự khóa theo văn phòng bán vé'}
            </small>
        </div>
    );
}

function CoachSummary({ lockedTrip }) {
    return (
        <div className="cargo-form-coach-summary" aria-label={`Xe thực hiện ${lockedTrip.licensePlate || 'chưa có biển số'}`}>
            <span>Xe thực hiện</span>
            <strong>{lockedTrip.licensePlate || 'Chưa gán biển số'}</strong>
            <small>Ghé văn phòng lúc {formatDateTime(lockedTrip.pickupTime)}</small>
        </div>
    );
}

function PartyCard({ title, prefix, data, onChange, setFormData }) {
    return (
        <Card className="shadow-sm border-0 h-100">
            <Card.Header className="bg-white py-3"><h5 className="fw-bold mb-0">{title}</h5></Card.Header>
            <Card.Body className="p-4 d-flex flex-column gap-3">
                <PhoneAutocomplete label="Số điện thoại" prefix={prefix} data={data} onChange={onChange} setFormData={setFormData} />
                <Field label="Họ tên" name={`${prefix}Name`} value={data[`${prefix}Name`]} onChange={onChange} required maxLength={100} />
            </Card.Body>
        </Card>
    );
}

function Field({ label, ...props }) {
    return (
        <Form.Group>
            <Form.Label className="fw-semibold">{label}{props.required ? ' *' : ''}</Form.Label>
            <Form.Control {...props} />
        </Form.Group>
    );
}

function Dropdown({
    label, options, optionValue, renderOption, loading,
    emptyLabel = '-- Chọn --', required = false, disabled = false, ...props
}) {
    return (
        <Form.Group>
            <Form.Label className="fw-semibold">{label}{required ? ' *' : ''}</Form.Label>
            <Form.Select {...props} required={required} disabled={loading || disabled}>
                <option value="">{loading ? 'Đang tải dữ liệu...' : emptyLabel}</option>
                {options.map((option) => (
                    <option key={option[optionValue]} value={option[optionValue]}>
                        {renderOption(option)}
                    </option>
                ))}
            </Form.Select>
        </Form.Group>
    );
}

const TRIP_STATUS_MAP = {
    SCHEDULED: 'Đã lên lịch',
    IN_PROGRESS: 'Đang chạy',
    COMPLETED: 'Hoàn thành',
    CANCELLED: 'Đã hủy'
};

const tripLabel = (trip) => {
    if (trip.staleCurrent) {
        return `Mã: ${trip.tripId} — chuyến hiện tại (không còn trong danh sách)`;
    }
    const time = trip.departureTime ? formatDateTime(trip.departureTime) : 'Chưa có giờ';
    const localizedStatus = trip.status ? (TRIP_STATUS_MAP[trip.status] || trip.status) : '';
    const coachType = trip.coachTypeName ? ` - Loại xe: ${trip.coachTypeName}` : '';
    return `Mã: ${trip.tripId} - Giờ chạy: ${time}${coachType} - [${localizedStatus}]`;
};

function formatDateTime(value) {
    if (!value) return 'Chưa có giờ';
    const date = new Date(value);
    const datePart = date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
    const timePart = date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', hour12: false });
    return `${timePart} ${datePart}`;
}

function filterTripsForSelectedStops(trips, stops, pickupStopId, dropoffStopId) {
    const hasPickup = stops.some((stop) => String(stop.stopPointId) === String(pickupStopId));
    const hasDropoff = stops.some((stop) => String(stop.stopPointId) === String(dropoffStopId));
    if (!hasPickup || !hasDropoff) {
        // Backend trips-by-stops already direction-filters; keep list while static stops lag.
        return trips;
    }
    return trips.filter((trip) => {
        if (trip.pickupStopId !== undefined && String(trip.pickupStopId) !== String(pickupStopId)) return false;
        if (trip.dropoffStopId !== undefined && String(trip.dropoffStopId) !== String(dropoffStopId)) return false;
        return true;
    });
}

function optionalId(value) {
    return value === '' || value === null || value === undefined ? null : Number(value);
}

function optionalText(value) {
    const normalized = String(value ?? '').trim();
    return normalized || null;
}

function isCargoTicketFormComplete(form, details, requireDimensions) {
    const requiredText = [form.senderName, form.senderPhone, form.receiverName, form.receiverPhone];
    const headerIsComplete = requiredText.every(value => String(value ?? '').trim())
        && Number(form.pickupStopId) > 0
        && Number(form.dropoffStopId) > 0
        && String(form.pickupStopId) !== String(form.dropoffStopId)
        && Number(form.soldBy) > 0
        && ['SENDER', 'RECEIVER'].includes(form.feePayer)
        && (form.feePayer === 'RECEIVER' || ['CASH', 'BANK_TRANSFER'].includes(form.paymentMethod));

    const detailsAreComplete = details.length > 0 && details.every(detail => {
        const baseIsComplete = Number(detail.cargoTypePriceId) > 0
            && Number.isInteger(Number(detail.quantity))
            && Number(detail.quantity) > 0
            && Number(detail.weightKg) > 0
            && Number(detail.dimensionVol) > 0;
        const dimensionsAreComplete = !requireDimensions
            || [detail.length, detail.width, detail.height].every(value => Number(value) > 0);
        return baseIsComplete && dimensionsAreComplete;
    });
    return headerIsComplete
        && detailsAreComplete
        && calculateOccupiedVolume(details) <= MAX_CARGO_VOLUME_M3;
}

function calculateOccupiedVolume(details) {
    return details.reduce((total, detail) => {
        const dimensionVol = Number(detail.dimensionVol);
        const quantity = Number(detail.quantity);
        if (!Number.isFinite(dimensionVol) || !Number.isFinite(quantity)) return total;
        return total + (dimensionVol * quantity);
    }, 0);
}

function formatVolume(value) {
    return Number(value).toLocaleString('vi-VN', { maximumFractionDigits: 6 });
}

function sumCalculatedPrices(details) {
    return (details || []).reduce((sum, detail) => sum + (Number(detail.calculatedPrice) || 0), 0);
}

function buildCargoTicketRequest(form, draftDetails) {
    const totalFromDetails = sumCalculatedPrices(draftDetails);
    return {
        tripId: optionalId(form.tripId),
        customerId: optionalId(form.customerId),
        senderName: form.senderName.trim(),
        senderPhone: form.senderPhone.trim(),
        receiverName: form.receiverName.trim(),
        receiverPhone: form.receiverPhone.trim(),
        totalPrice: totalFromDetails > 0 ? totalFromDetails : Number(form.totalPrice) || 0,
        description: optionalText(form.description),
        feePayer: form.feePayer,
        codAmount: Number.isFinite(Number(form.codAmount)) ? Number(form.codAmount) : 0,
        pickupStopId: Number(form.pickupStopId),
        dropoffStopId: Number(form.dropoffStopId),
        status: form.status,
        soldBy: form.soldBy ? { staffId: Number(form.soldBy) } : null,
        paymentMethod: form.feePayer === 'RECEIVER' ? null : form.paymentMethod,
        details: (draftDetails || []).map(d => ({
            cargoTicketDetailId: d.cargoTicketDetailId,
            cargoTypePriceId: Number(d.cargoTypePriceId),
            description: optionalText(d.description),
            quantity: Number(d.quantity),
            weightKg: Number(d.weightKg),
            // Stabilize volume so paid trip-only updates do not look like price edits.
            dimensionVol: safeRound(Number(d.dimensionVol), 6)
        }))
    };
}
