import { useState, useMemo, useEffect } from 'react';
import { Badge, Button, Col, Row, Form } from 'react-bootstrap';
import { BsSearch } from 'react-icons/bs';
import {
    DndContext,
    closestCorners,
    KeyboardSensor,
    PointerSensor,
    useSensor,
    useSensors,
    useDroppable,
    DragOverlay
} from '@dnd-kit/core';
import {
    arrayMove,
    SortableContext,
    sortableKeyboardCoordinates,
    verticalListSortingStrategy,
    useSortable
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

// --- Sortable Item Component ---
function SortableStopItem({ stop, isSelected, orderIndex }) {
    const {
        attributes,
        listeners,
        setNodeRef,
        transform,
        transition,
        isDragging,
    } = useSortable({ id: String(stop.stopPointId) });

    const style = {
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.4 : 1,
        zIndex: isDragging ? 999 : 'auto',
    };

    return (
        <div
            ref={setNodeRef}
            style={{ ...style, cursor: 'grab' }}
            className={`d-flex align-items-center gap-3 p-3 mb-2 rounded shadow-sm border ${isSelected ? 'bg-white border-primary' : 'bg-white border-secondary'}`}
            {...attributes}
            {...listeners}
        >
            {isSelected && (
                <div className="custom-btn-general text-white rounded-circle d-flex align-items-center justify-content-center fw-bold" style={{ width: '28px', height: '28px', fontSize: '0.9rem' }}>
                    {orderIndex + 1}
                </div>
            )}

            <div className="flex-fill">
                <div className="fw-bold text-dark">{stop.stopPointName}</div>
                <small className="text-muted">{stop.city} {stop.address ? `- ${stop.address}` : ''}</small>
            </div>
        </div>
    );
}

// --- Droppable Container Component ---
function DroppableContainer({ id, items, title, badgeCount, badgeColor, onClear, searchBox, children }) {
    const { setNodeRef, isOver } = useDroppable({ id });

    return (
        <div className="d-flex flex-column h-100">
            <div className="d-flex justify-content-between align-items-center mb-3">
                <div className="d-flex align-items-center gap-2">
                    <h6 className="fw-bold text-dark mb-0">{title}</h6>
                    <Badge bg={badgeColor} pill>{badgeCount}</Badge>
                </div>
                {onClear && items.length > 0 && (
                    <Button className="fw-medium m-0 form-check form-switch" variant="outline-danger" size="sm" onClick={onClear} style={{ fontSize: '1.1rem', padding: '0.15rem 0.5rem' }}>
                        Xóa tất cả
                    </Button>
                )}
            </div>
            {searchBox && <div className="mb-3">{searchBox}</div>}
            <div
                ref={setNodeRef}
                className="flex-grow-1 p-3 rounded"
                style={{
                    minHeight: '400px',
                    maxHeight: '500px',
                    overflowY: 'auto',
                    backgroundColor: isOver ? '#e2e8f0' : '#f8f9fa',
                    border: '2px dashed #cbd5e1',
                    transition: 'background-color 0.2s'
                }}
            >
                <SortableContext id={id} items={items} strategy={verticalListSortingStrategy}>
                    {children}
                </SortableContext>
                {items.length === 0 && (
                    <div className="d-flex h-100 align-items-center justify-content-center text-muted fst-italic">
                        Kéo thả vào đây
                    </div>
                )}
            </div>
        </div>
    );
}

export default function RouteStopsDndManager({ available, setAvailable, selected, setSelected }) {
    const [activeId, setActiveId] = useState(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedSearchQuery, setSelectedSearchQuery] = useState('');

    // Debounced search queries
    const [debouncedSearchQuery, setDebouncedSearchQuery] = useState('');
    const [debouncedSelectedSearchQuery, setDebouncedSelectedSearchQuery] = useState('');

    useEffect(() => {
        const timerId = setTimeout(() => {
            setDebouncedSearchQuery(searchQuery);
        }, 300);
        return () => clearTimeout(timerId);
    }, [searchQuery]);

    useEffect(() => {
        const timerId = setTimeout(() => {
            setDebouncedSelectedSearchQuery(selectedSearchQuery);
        }, 300);
        return () => clearTimeout(timerId);
    }, [selectedSearchQuery]);

    const filteredAvailable = useMemo(() => {
        if (!debouncedSearchQuery) return available;
        const lowerQuery = debouncedSearchQuery.toLowerCase();
        return available.filter(stop =>
            (stop.stopPointName && stop.stopPointName.toLowerCase().includes(lowerQuery)) ||
            (stop.city && stop.city.toLowerCase().includes(lowerQuery)) ||
            (stop.address && stop.address.toLowerCase().includes(lowerQuery))
        );
    }, [available, debouncedSearchQuery]);

    const filteredSelected = useMemo(() => {
        if (!debouncedSelectedSearchQuery) return selected;
        const lowerQuery = debouncedSelectedSearchQuery.toLowerCase();
        return selected.filter(stop =>
            (stop.stopPointName && stop.stopPointName.toLowerCase().includes(lowerQuery)) ||
            (stop.city && stop.city.toLowerCase().includes(lowerQuery)) ||
            (stop.address && stop.address.toLowerCase().includes(lowerQuery))
        );
    }, [selected, debouncedSelectedSearchQuery]);

    const sensors = useSensors(
        useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
        useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
    );

    const findContainer = (id) => {
        if (id === 'available' || id === 'selected') return id;
        if (available.find(s => String(s.stopPointId) === String(id))) return 'available';
        if (selected.find(s => String(s.stopPointId) === String(id))) return 'selected';
        return null;
    };

    const handleDragStart = (event) => {
        setActiveId(event.active.id);
    };

    const handleDragOver = (event) => {
        const { active, over } = event;
        const overId = over?.id;

        if (!overId) return;

        const activeContainer = findContainer(active.id);
        const overContainer = findContainer(overId);

        if (!activeContainer || !overContainer || activeContainer === overContainer) {
            return;
        }

        // Moving item between lists
        if (activeContainer === 'available' && overContainer === 'selected') {
            const item = available.find(s => String(s.stopPointId) === String(active.id));
            setAvailable(prev => prev.filter(s => String(s.stopPointId) !== String(active.id)));
            setSelected(prev => {
                const overIndex = prev.findIndex(s => String(s.stopPointId) === String(overId));
                const newIndex = overIndex >= 0 ? overIndex : prev.length;
                const copy = [...prev];
                copy.splice(newIndex, 0, item);
                return copy;
            });
        } else if (activeContainer === 'selected' && overContainer === 'available') {
            const item = selected.find(s => String(s.stopPointId) === String(active.id));
            setSelected(prev => prev.filter(s => String(s.stopPointId) !== String(active.id)));
            setAvailable(prev => {
                const overIndex = prev.findIndex(s => String(s.stopPointId) === String(overId));
                const newIndex = overIndex >= 0 ? overIndex : prev.length;
                const copy = [...prev];
                copy.splice(newIndex, 0, item);
                return copy;
            });
        }
    };

    const handleDragEnd = (event) => {
        const { active, over } = event;
        setActiveId(null);

        if (!over) return;

        const activeContainer = findContainer(active.id);
        const overContainer = findContainer(over.id);

        if (activeContainer && activeContainer === overContainer) {
            // Reordering within the SAME list
            if (activeContainer === 'selected') {
                const oldIndex = selected.findIndex(s => String(s.stopPointId) === String(active.id));
                const newIndex = selected.findIndex(s => String(s.stopPointId) === String(over.id));
                if (oldIndex !== newIndex) {
                    setSelected(arrayMove(selected, oldIndex, newIndex));
                }
            } else {
                const oldIndex = available.findIndex(s => String(s.stopPointId) === String(active.id));
                const newIndex = available.findIndex(s => String(s.stopPointId) === String(over.id));
                if (oldIndex !== newIndex) {
                    setAvailable(arrayMove(available, oldIndex, newIndex));
                }
            }
        }
    };

    const activeItem = activeId
        ? [...available, ...selected].find(s => String(s.stopPointId) === String(activeId))
        : null;

    return (
        <DndContext
            sensors={sensors}
            collisionDetection={closestCorners}
            onDragStart={handleDragStart}
            onDragOver={handleDragOver}
            onDragEnd={handleDragEnd}
        >
            <Row>
                <Col md={6}>
                    <DroppableContainer
                        id="available"
                        title="Trạm dừng có sẵn"
                        items={filteredAvailable.map(s => String(s.stopPointId))}
                        badgeCount={filteredAvailable.length}
                        badgeColor="secondary"
                        searchBox={
                            <div className="position-relative">
                                <BsSearch
                                    size={14}
                                    className="position-absolute text-secondary"
                                    style={{ left: '10px', top: '50%', transform: 'translateY(-50%)' }}
                                />
                                <Form.Control
                                    type="text"
                                    size="sm"
                                    placeholder="Tìm kiếm..."
                                    value={searchQuery}
                                    onChange={(e) => setSearchQuery(e.target.value)}
                                    style={{ paddingLeft: '32px' }}
                                    className="rounded-pill"
                                />
                            </div>
                        }
                    >
                        {filteredAvailable.map(stop => (
                            <SortableStopItem key={stop.stopPointId} stop={stop} isSelected={false} />
                        ))}
                    </DroppableContainer>
                </Col>
                <Col md={6}>
                    <DroppableContainer
                        id="selected"
                        title="Lộ trình tuyến"
                        items={filteredSelected.map(s => String(s.stopPointId))}
                        badgeCount={filteredSelected.length}
                        badgeColor="secondary"
                        onClear={() => {
                            setAvailable(prev => [...prev, ...selected]);
                            setSelected([]);
                        }}
                        searchBox={
                            <div className="position-relative">
                                <BsSearch
                                    size={14}
                                    className="position-absolute text-secondary"
                                    style={{ left: '10px', top: '50%', transform: 'translateY(-50%)' }}
                                />
                                <Form.Control
                                    type="text"
                                    size="sm"
                                    placeholder="Tìm kiếm..."
                                    value={selectedSearchQuery}
                                    onChange={(e) => setSelectedSearchQuery(e.target.value)}
                                    style={{ paddingLeft: '32px' }}
                                    className="rounded-pill"
                                />
                            </div>
                        }
                    >
                        {filteredSelected.map((stop) => {
                            const originalIndex = selected.findIndex(s => s.stopPointId === stop.stopPointId);
                            return (
                                <SortableStopItem key={stop.stopPointId} stop={stop} isSelected={true} orderIndex={originalIndex} />
                            );
                        })}
                    </DroppableContainer>
                </Col>
            </Row>
            <DragOverlay>
                {activeItem ? (
                    <div className="p-3 bg-white border border-primary rounded shadow text-dark fw-bold">
                        {activeItem.stopPointName}
                    </div>
                ) : null}
            </DragOverlay>
        </DndContext>
    );
}
