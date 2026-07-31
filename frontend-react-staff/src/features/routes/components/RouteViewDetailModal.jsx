import { useCallback, useEffect, useMemo, useState } from "react"
import { routeApi } from "../api/routeApi";
import { coachStopApi } from "../../coachStops/api/coachStopApi";
import { routeStopApi } from "../api/routeStopApi";

import { Alert, Badge, Button, Form, Modal, Spinner, Table } from "react-bootstrap";
import { BsExclamationTriangleFill, BsTrash, BsPencilFill, BsPlusCircleFill, BsGeoAltFill, BsXLg, BsSearch } from "react-icons/bs";
import { FaArrowRightLong } from "react-icons/fa6";
import { TiTick } from "react-icons/ti";
import { arrayMove, SortableContext, sortableKeyboardCoordinates, verticalListSortingStrategy } from '@dnd-kit/sortable';
import AvailableCoachStopsPanel from "./AvailableCoachStopsPanel";
import DraftRouteStopsTable from "./DraftRouteStopsTable";

const INITIAL_DETAIL = {
    routeId: '',
    routeName: '',
    totalKilometers: '',
    totalMinutes: '',
    active: '',
    routeStops: []
}

export default function RouteViewDetailModal({ isOpen, data, onClose }) {
    const [detail, setDetail] = useState(INITIAL_DETAIL);
    const [originalRouteStops, setOriginalRouteStops] = useState([]);
    const [draftRouteStops, setDraftRouteStops] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);


    // Right panel state
    const [showRightPanel, setShowRightPanel] = useState(false);
    const [coachStops, setCoachStops] = useState([]);
    const [coachStopsLoading, setCoachStopsLoading] = useState(false);
    const [coachStopSearch, setCoachStopSearch] = useState('');
    const [isClosingRightPanel, setIsClosingRightPanel] = useState(false);
    const [calculatingDistances, setCalculatingDistances] = useState(false);
    const [pendingCoachStop, setPendingCoachStop] = useState(null);
    const [isDeleteMode, setIsDeleteMode] = useState(false);
    const [selectedForDeletion, setSelectedForDeletion] = useState([]);

    const firstStop = useMemo(() => {
        if (!draftRouteStops || draftRouteStops.length === 0) return null;
        return draftRouteStops.reduce((min, s) => s.stopOrder < min.stopOrder ? s : min, draftRouteStops[0]);
    }, [draftRouteStops]);

    const lastStop = useMemo(() => {
        if (!draftRouteStops || draftRouteStops.length === 0) return null;
        return draftRouteStops.reduce((max, s) => s.stopOrder > max.stopOrder ? s : max, draftRouteStops[0]);
    }, [draftRouteStops]);

    // Filter out stops already assigned to this route
    const availableCoachStops = useMemo(() => {
        const existingStopIds = (draftRouteStops || []).map(rs => rs.stopPointId);
        let filtered = coachStops.filter(cs => !existingStopIds.includes(cs.stopPointId));
        if (coachStopSearch.trim()) {
            const search = coachStopSearch.toLowerCase().trim();
            filtered = filtered.filter(cs =>
                cs.stopPointName?.toLowerCase().includes(search) ||
                cs.address?.toLowerCase().includes(search) ||
                cs.city?.toLowerCase().includes(search)
            );
        }
        return filtered;
    }, [coachStops, draftRouteStops, coachStopSearch]);

    const handleAddCoachStop = (coachStop) => {
        if (pendingCoachStop?.stopPointId === coachStop.stopPointId) {
            setPendingCoachStop(null);
            return;
        }

        if (draftRouteStops.length === 0) {
            const newStop = {
                id: `draft-${Date.now()}`,
                stopPointId: coachStop.stopPointId,
                stopPointName: coachStop.stopPointName,
                city: coachStop.city,
                stopOrder: 1,
                kilometersFromStart: 0,
                minutesFromStart: 0,
                stopPointActive: coachStop.active !== undefined ? coachStop.active : true
            };
            setDraftRouteStops([newStop]);
        } else {
            setPendingCoachStop(coachStop);
        }
    };

    const handleInsertPendingStop = (index) => {
        if (!pendingCoachStop) return;

        const newStop = {
            id: `draft-${Date.now()}`,
            stopPointId: pendingCoachStop.stopPointId,
            stopPointName: pendingCoachStop.stopPointName,
            city: pendingCoachStop.city,
            stopOrder: 0,
            kilometersFromStart: 0,
            minutesFromStart: 0,
            stopPointActive: pendingCoachStop.active !== undefined ? pendingCoachStop.active : true
        };

        const copy = [...draftRouteStops];
        copy.splice(index + 1, 0, newStop);

        const updatedStops = copy.map((stop, i) => ({
            ...stop,
            stopOrder: i + 1
        }));

        setDraftRouteStops(updatedStops);
        setPendingCoachStop(null);
    };

    const handleDragEnd = (event) => {
        const { active, over } = event;
        if (!over || active.id === over.id) return;

        const oldIndex = draftRouteStops.findIndex(stop => (stop.routeStopId || stop.id) === active.id);
        const newIndex = draftRouteStops.findIndex(stop => (stop.routeStopId || stop.id) === over.id);

        if (oldIndex !== -1 && newIndex !== -1) {
            const newStops = arrayMove(draftRouteStops, oldIndex, newIndex);

            const updatedStops = newStops.map((stop, index) => ({
                ...stop,
                stopOrder: index + 1
            }));

            setDraftRouteStops(updatedStops);
        }
    };

    const fetchRouteDetail = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            if (isOpen && data) {
                const res = await routeApi.getRouteDetail(data.routeId)
                const routeStops = res.routeStops || [];

                const stopsWithActiveStatus = await Promise.all(
                    routeStops.map(async (stop) => {
                        try {
                            const csRes = await coachStopApi.getCoachStopById(stop.stopPointId);
                            // Set the property we use in the render logic
                            return { ...stop, stopPointActive: csRes.active !== undefined ? csRes.active : true };
                        } catch (e) {
                            return { ...stop, stopPointActive: true };
                        }
                    })
                );

                stopsWithActiveStatus.sort((a, b) => a.stopOrder - b.stopOrder);

                setOriginalRouteStops(structuredClone(stopsWithActiveStatus));
                setDraftRouteStops(structuredClone(stopsWithActiveStatus));

                setDetail({
                    routeId: res.routeId,
                    routeName: res.routeName,
                    totalKilometers: res.totalKilometers,
                    totalMinutes: res.totalMinutes,
                    active: res.active !== undefined ? res.active : res.active, // Handle API field name
                });
            }
        } catch (error) {
            setError(error.response?.data?.message || "Có lỗi xảy ra khi lấy dữ liệu.");
        } finally {
            setLoading(false);
        }
    }, [data, isOpen])

    const fetchCoachStops = useCallback(async () => {
        setCoachStopsLoading(true);
        try {
            const data = await coachStopApi.getAllCoachStops('', true, 0, 1000);
            setCoachStops(data.content || []);
        } catch (err) {
            console.error("Lỗi khi tải danh sách trạm dừng:", err);
        } finally {
            setCoachStopsLoading(false);
        }
    }, []);

    useEffect(() => {
        const load = () => {
            fetchRouteDetail();
        }
        load();
    }, [fetchRouteDetail])

    // Fetch coach stops when right panel opens
    useEffect(() => {
        if (showRightPanel) {
            fetchCoachStops();
        }
    }, [showRightPanel, fetchCoachStops]);

    // Reset right panel when modal closes
    useEffect(() => {
        if (!isOpen) {
            setShowRightPanel(false);
            setIsClosingRightPanel(false);
            setCoachStopSearch('');
            setPendingCoachStop(null);
            setIsDeleteMode(false);
            setSelectedForDeletion([]);
        }
    }, [isOpen]);

    // Handle "Hoàn Tất" — sync draft to backend and calculate distances
    const handleCalculateDistances = async () => {
        const isDraftModified = JSON.stringify(draftRouteStops.map(s => s.routeStopId || s.id))
            !== JSON.stringify(originalRouteStops.map(s => s.routeStopId));

        const hasZeroStops = draftRouteStops.some(
            s => Number(s.kilometersFromStart) === 0 && Number(s.minutesFromStart) === 0
        );

        if (!isDraftModified && !hasZeroStops) {
            // No changes, just close the panel
            setIsClosingRightPanel(true);
            setTimeout(() => {
                setShowRightPanel(false);
                setIsClosingRightPanel(false);
            }, 240);
            return;
        }

        setCalculatingDistances(true);
        try {
            if (isDraftModified) {
                // 1. Delete removed stops
                const originalIds = originalRouteStops.map(s => s.routeStopId);
                const draftIds = draftRouteStops.map(s => s.routeStopId).filter(Boolean);
                const deletedIds = originalIds.filter(id => !draftIds.includes(id));

                if (draftRouteStops.length < 2) {
                    alert("Không thể lưu. Một tuyến đường phải có ít nhất 2 trạm dừng.");
                    return;
                }

                for (const id of deletedIds) {
                    await routeStopApi.deleteRouteStop(id);
                }

                // 2. Add new stops
                for (let i = 0; i < draftRouteStops.length; i++) {
                    const stop = draftRouteStops[i];
                    if (!stop.routeStopId) {
                        await routeStopApi.createRouteStop({
                            routeId: Number(detail.routeId),
                            stopPointId: stop.stopPointId,
                            stopOrder: i + 1,
                            kilometersFromStart: 0,
                            minutesFromStart: 0
                        });
                    }
                }

                // 3. Sync orders
                const fetchRes = await routeApi.getRouteDetail(detail.routeId);
                const fetchedStops = fetchRes.routeStops || [];

                const payload = draftRouteStops.map((draftStop, index) => {
                    const matched = fetchedStops.find(fs => fs.stopPointId === draftStop.stopPointId);
                    return {
                        routeStopId: matched?.routeStopId,
                        stopOrder: index + 1
                    };
                }).filter(p => p.routeStopId);

                if (payload.length > 0) {
                    await routeStopApi.bulkUpdateOrders(payload);
                }
            }

            // 4. Calculate distances
            await routeStopApi.calculateDistances({ routeId: Number(detail.routeId) });
            await fetchRouteDetail();

            // Close the right panel after success
            setIsClosingRightPanel(true);
            setTimeout(() => {
                setShowRightPanel(false);
                setIsClosingRightPanel(false);
            }, 240);
        } catch (err) {
            alert(err.response?.data?.message || "Có lỗi xảy ra khi lưu thay đổi.");
            fetchRouteDetail(); // Revert to backend state on error
        } finally {
            setCalculatingDistances(false);
        }
    };

    const handleConfirmDelete = async () => {
        if (selectedForDeletion.length === 0) {
            setIsDeleteMode(false);
            return;
        }

        if (originalRouteStops.length - selectedForDeletion.length < 2) {
            alert("Không thể xóa. Một tuyến đường phải có ít nhất 2 trạm dừng.");
            return;
        }

        setCalculatingDistances(true);
        try {
            for (const id of selectedForDeletion) {
                await routeStopApi.deleteRouteStop(id);
            }
            await fetchRouteDetail();
            setIsDeleteMode(false);
            setSelectedForDeletion([]);
        } catch (err) {
            alert(err.response?.data?.message || "Có lỗi xảy ra khi xóa trạm dừng.");
        } finally {
            setCalculatingDistances(false);
        }
    };

    if (!isOpen) return null;

    return (
        <Modal
            show={isOpen}
            onHide={onClose}
            size={showRightPanel ? 'xl' : 'lg'}
            centered
            backdrop="static"
        >
            <Modal.Header closeButton>
                <Modal.Title className="fs-5 fw-bold text-primary">
                    Chi tiết tuyến đường
                </Modal.Title>
            </Modal.Header>

            <Modal.Body className="px-0 py-0">
                <div className="d-flex" style={{ minHeight: '400px' }}>
                    {/* ===== LEFT COLUMN: Route detail ===== */}
                    <div
                        className="px-4 py-4 overflow-auto"
                        style={{
                            flex: showRightPanel ? '2 1 0%' : '1 1 100%',
                            transition: 'flex 0.3s ease',
                            maxHeight: '70vh'
                        }}
                    >
                        {loading ? (
                            <div className="d-flex flex-column align-items-center justify-content-center py-5">
                                <Spinner animation="border" variant="primary" />
                                <span className="mt-2 text-secondary">Đang tải thông tin...</span>
                            </div>
                        ) : error ? (
                            <Alert variant="danger" className="mb-3 py-4 px-4 d-flex align-items-center gap-2">
                                <BsExclamationTriangleFill />
                                <span>{error}</span>
                            </Alert>
                        ) : (
                            <div className="d-flex flex-column gap-4">
                                {/* Route Origin → Destination Summary */}
                                <div className="bg-light rounded-3 p-3 border">
                                    <div className="d-flex align-items-center justify-content-between mb-3">
                                        <span className="fw-bold text-dark">Tổng quan tuyến đường</span>
                                        <div className="d-flex gap-2">
                                            {showRightPanel && (
                                                <>
                                                    <Button
                                                        size="sm"
                                                        variant="success"
                                                        className="d-flex align-items-center gap-1 text-white"
                                                        onClick={() => {
                                                            handleCalculateDistances()
                                                            setDraftRouteStops(structuredClone(originalRouteStops));
                                                            setPendingCoachStop(null);
                                                        }}
                                                        disabled={isClosingRightPanel || calculatingDistances}
                                                    >
                                                        {calculatingDistances ? (
                                                            <><Spinner animation="border" size="sm" style={{ width: '16px', height: '16px' }} /> Đang tính...</>
                                                        ) : (
                                                            <span className='fw-medium'><TiTick size={18} /> Hoàn tất</span>
                                                        )}
                                                    </Button>
                                                    <Button
                                                        size="sm"
                                                        variant="danger"
                                                        className="fw-medium d-flex align-items-center gap-1"
                                                        onClick={() => {
                                                            setDraftRouteStops(structuredClone(originalRouteStops));
                                                            setPendingCoachStop(null);
                                                            setIsClosingRightPanel(true);
                                                            setTimeout(() => {
                                                                setShowRightPanel(false);
                                                                setIsClosingRightPanel(false);
                                                            }, 240);
                                                        }}
                                                        disabled={isClosingRightPanel}
                                                    >
                                                        <BsXLg size={12} /> Hủy
                                                    </Button>
                                                </>
                                            )}

                                            {isDeleteMode && (
                                                <>
                                                    <Button
                                                        size="sm"
                                                        variant="success"
                                                        className="d-flex align-items-center gap-1 text-white"
                                                        onClick={handleConfirmDelete}
                                                        disabled={calculatingDistances}
                                                    >
                                                        {calculatingDistances ? (
                                                            <><Spinner animation="border" size="sm" style={{ width: '16px', height: '16px' }} /> Đang xử lý...</>
                                                        ) : (
                                                            <span className='fw-medium'><TiTick size={18} /> Hoàn tất</span>
                                                        )}
                                                    </Button>
                                                    <Button
                                                        size="sm"
                                                        variant="danger"
                                                        className="fw-medium d-flex align-items-center gap-1"
                                                        onClick={() => {
                                                            setIsDeleteMode(false);
                                                            setSelectedForDeletion([]);
                                                        }}
                                                        disabled={calculatingDistances}
                                                    >
                                                        <BsXLg size={12} /> Hủy
                                                    </Button>
                                                </>
                                            )}

                                            {!showRightPanel && !isDeleteMode && (
                                                <>
                                                    <Button
                                                        size="sm"
                                                        variant="danger"
                                                        className="fw-medium d-flex align-items-center gap-1"
                                                        onClick={() => setIsDeleteMode(true)}
                                                    >
                                                        Xóa điểm dừng
                                                    </Button>
                                                    <Button
                                                        size="sm"
                                                        className="fw-medium d-flex align-items-center gap-1 custom-btn-general"
                                                        onClick={() => setShowRightPanel(true)}
                                                    >
                                                        Chỉnh sửa tuyến đường
                                                    </Button>
                                                </>
                                            )}
                                        </div>
                                    </div>
                                    <div className="d-flex align-items-center gap-2">
                                        {/* First stop (origin) */}
                                        <div className="d-flex align-items-center gap-2 flex-fill">
                                            <BsGeoAltFill size={18} className="text-success flex-shrink-0" />
                                            <div>
                                                <small className="text-secondary d-block" style={{ fontSize: '0.7rem' }}>ĐIỂM ĐẦU</small>
                                                <span className="fw-semibold text-dark" style={{ fontSize: '0.9rem' }}>
                                                    {firstStop ? firstStop.stopPointName : '—'}
                                                </span>
                                            </div>
                                        </div>

                                        {/* Connector */}
                                        <div className="flex-fill d-flex align-items-center px-2 justify-content-center" style={{ minWidth: '40px' }}>
                                            <FaArrowRightLong size={30} className="text-secondary flex-shrink-0" />
                                        </div>

                                        {/* Last stop (destination) */}
                                        <div className="d-flex align-items-center gap-2 flex-fill justify-content-end text-end">
                                            <div>
                                                <small className="text-secondary d-block" style={{ fontSize: '0.7rem' }}>ĐIỂM CUỐI</small>
                                                <span className="fw-semibold text-dark" style={{ fontSize: '0.9rem' }}>
                                                    {lastStop ? lastStop.stopPointName : '—'}
                                                </span>
                                            </div>
                                            <BsGeoAltFill size={18} className="text-danger flex-shrink-0" />
                                        </div>
                                    </div>
                                </div>

                                <div>
                                    <h6 className="fw-bold text-dark border-bottom pb-2 mb-3">Các trạm dừng</h6>
                                    {draftRouteStops && draftRouteStops.length > 0 ? (
                                        <div className="table-responsive">
                                            <DraftRouteStopsTable
                                                draftRouteStops={draftRouteStops}
                                                onDragEnd={handleDragEnd}
                                                isDraftMode={showRightPanel}
                                                pendingCoachStop={pendingCoachStop}
                                                handleInsertPendingStop={handleInsertPendingStop}
                                                isDeleteMode={isDeleteMode}
                                                selectedForDeletion={selectedForDeletion}
                                                setSelectedForDeletion={setSelectedForDeletion}
                                            />
                                        </div>
                                    ) : (
                                        <div className="bg-light p-4 rounded border text-center">
                                            <p className="text-muted mb-0 fst-italic">Tuyến đường này chưa được cấu hình trạm dừng nào.</p>
                                        </div>
                                    )}
                                </div>
                            </div>
                        )}
                    </div>

                    {/* ===== RIGHT COLUMN: Coach stops list ===== */}
                    <AvailableCoachStopsPanel
                        showRightPanel={showRightPanel}
                        isClosingRightPanel={isClosingRightPanel}
                        availableCoachStops={availableCoachStops}
                        coachStopSearch={coachStopSearch}
                        setCoachStopSearch={setCoachStopSearch}
                        coachStopsLoading={coachStopsLoading}
                        routeStops={draftRouteStops}
                        handleAddCoachStop={handleAddCoachStop}
                        pendingCoachStop={pendingCoachStop}
                    />
                </div>
            </Modal.Body>

            <Modal.Footer className="bg-light border-0 rounded-bottom">
                <Button variant="outline-secondary" onClick={onClose} className="px-4">
                    Đóng
                </Button>
            </Modal.Footer>

            {/* Inline styles for slide-in animation and hover */}
            <style>{`
                @keyframes slideInRight {
                    from {
                        opacity: 0;
                        transform: translateX(20px);
                    }
                    to {
                        opacity: 1;
                        transform: translateX(0);
                    }
                }
                @keyframes slideOutRight {
                    from {
                        opacity: 1;
                        transform: translateX(0);
                    }
                    to {
                        opacity: 0;
                        transform: translateX(20px);
                    }
                }
                .coach-stop-item:hover {
                    background-color: #e8f0fe !important;
                    border-color: #4285f4 !important;
                }
            `}</style>
        </Modal>
    );
}
