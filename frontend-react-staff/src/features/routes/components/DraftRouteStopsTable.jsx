import { DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors } from '@dnd-kit/core';
import { arrayMove, SortableContext, sortableKeyboardCoordinates, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { Table } from 'react-bootstrap';
import SortableRouteStopRow from './SortableRouteStopRow';

export default function DraftRouteStopsTable({ 
    draftRouteStops, onDragEnd, isDraftMode, 
    pendingCoachStop, handleInsertPendingStop,
    isDeleteMode, selectedForDeletion, setSelectedForDeletion 
}) {
    const toggleSelection = (routeStopId) => {
        if (!routeStopId) return;
        const alreadySelected = selectedForDeletion.includes(routeStopId);
        if (!alreadySelected) {
            // Block selection if deleting this stop would leave fewer than 2
            const remainingCount = draftRouteStops.length - selectedForDeletion.length - 1;
            if (remainingCount < 2) return;
        }
        setSelectedForDeletion(prev =>
            alreadySelected
                ? prev.filter(id => id !== routeStopId)
                : [...prev, routeStopId]
        );
    };
    const sensors = useSensors(
        useSensor(PointerSensor),
        useSensor(KeyboardSensor, {
            coordinateGetter: sortableKeyboardCoordinates,
        })
    );

    return (
        <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            onDragEnd={onDragEnd}
        >
            <Table size="sm" bordered hover className="align-middle text-center mb-0">
                <thead className="table-light text-secondary">
                    <tr>
                        {isDeleteMode && <th className="fw-semibold" style={{ width: '50px' }}>Xóa</th>}
                        <th className="fw-semibold" style={{ width: '120px' }}>Thứ tự</th>
                        <th className="fw-semibold text-start just">Tên trạm</th>
                        <th className="fw-semibold text-start">Thành phố / Tỉnh</th>
                        <th className="fw-semibold">Khoảng cách từ điểm xuất phát</th>
                        <th className="fw-semibold">Thời gian từ điểm xuất phát</th>
                    </tr>
                </thead>
                <tbody>
                    <SortableContext
                        items={draftRouteStops.map(s => s.routeStopId || s.id)}
                        strategy={verticalListSortingStrategy}
                    >
                        {draftRouteStops.map((stop, index) => (
                            <SortableRouteStopRow
                                key={stop.routeStopId || stop.id}
                                stop={stop}
                                isDraftMode={isDraftMode}
                                pendingCoachStop={pendingCoachStop}
                                onInsertPending={() => handleInsertPendingStop(index)}
                                isDeleteMode={isDeleteMode}
                                isSelected={selectedForDeletion?.includes(stop.routeStopId)}
                                onToggleSelection={() => toggleSelection(stop.routeStopId)}
                            />
                        ))}
                    </SortableContext>
                </tbody>
            </Table>
        </DndContext>
    );
}
