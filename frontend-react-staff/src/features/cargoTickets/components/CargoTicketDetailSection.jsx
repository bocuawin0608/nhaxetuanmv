import { Button, Form } from 'react-bootstrap';
import { useCargoTypes } from '../../cargo/hooks/useCargoTypes';
import { useEffect, useState } from 'react';
import { BsTrash, BsPlus } from 'react-icons/bs';
import { cargoTicketApi } from '../api/cargoTicketApi';
import { safeRound } from '../../../utils/formatters';

function Field({ label, required, suffix, ...props }) {
    return (
        <Form.Group>
            <Form.Label className="small text-muted fw-semibold mb-1">
                {label}{required && <span className="text-danger ms-1">*</span>}
            </Form.Label>
            {suffix ? (
                <div className="position-relative">
                    <Form.Control {...props} required={required} aria-required={required} style={{ paddingRight: '40px' }} />
                    <span className="position-absolute end-0 top-50 translate-middle-y me-3 text-muted small" style={{ pointerEvents: 'none' }}>
                        {suffix}
                    </span>
                </div>
            ) : (
                <Form.Control {...props} required={required} aria-required={required} />
            )}
        </Form.Group>
    );
}

function DetailItem({
    detail, index, cargoTypes, armedDeleteIndex, setArmedDeleteIndex, onChange, onRemove, readOnly
}) {
    const isArmed = armedDeleteIndex === index;

    const handleDeleteClick = () => {
        if (readOnly) return;
        if (isArmed) {
            onRemove(index);
            setArmedDeleteIndex(null);
        } else {
            setArmedDeleteIndex(index);
        }
    };

    /** Recalculates volume locally as soon as all three dimensions are valid. */
    const handleDimensionChange = (field, value) => {
        if (readOnly) return;
        onChange(index, field, value);
        const dimensions = { length: detail.length, width: detail.width, height: detail.height, [field]: value };
        if ([dimensions.length, dimensions.width, dimensions.height].every(item => Number(item) > 0)) {
            const rawVol = Number(dimensions.length) * Number(dimensions.width) * Number(dimensions.height);
            onChange(index, 'dimensionVol', safeRound(rawVol, 6));
        }
    };

    useEffect(() => {
        if (readOnly) return;
        const vol = Number(detail.dimensionVol);
        const weight = Number(detail.weightKg);
        const qty = Number(detail.quantity);

        if (!detail.cargoTypePriceId || !qty || vol <= 0 || weight <= 0) {
            if (detail.calculatedPrice !== undefined) {
                onChange(index, 'calculatedPrice', undefined);
            }
            return;
        }

        const timeoutId = setTimeout(() => {
            cargoTicketApi.calculatePrice({
                cargoTypePriceId: Number(detail.cargoTypePriceId),
                dimensionVol: vol,
                quantity: qty
            }).then(res => {
                if (detail.calculatedPrice !== res.calculatedPrice) {
                    onChange(index, 'calculatedPrice', res.calculatedPrice);
                }
            }).catch(console.error);
        }, 300);

        return () => clearTimeout(timeoutId);
    }, [readOnly, detail.cargoTypePriceId, detail.dimensionVol, detail.quantity, detail.calculatedPrice, detail.weightKg, index, onChange]);

    return (
        <div className="bg-light" style={{ borderRadius: '12px', padding: '1rem' }}>
            <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="fw-bold">Hàng hóa #{index + 1}</span>
                {!readOnly && (
                    <Button
                        variant={isArmed ? 'danger' : 'outline-secondary'}
                        size="sm"
                        className="d-flex align-items-center justify-content-center p-0"
                        style={{ width: '32px', height: '32px' }}
                        onClick={handleDeleteClick}
                        aria-label={isArmed ? "Xác nhận xóa hàng hóa này" : "Xóa hàng hóa này"}
                    >
                        <BsTrash size={16} />
                    </Button>
                )}
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '16px', marginBottom: '16px' }}>
                <Form.Group>
                    <Form.Label className="small text-muted fw-semibold mb-1">
                        Loại hàng <span className="text-danger ms-1">*</span>
                    </Form.Label>
                    <Form.Select
                        value={detail.cargoTypePriceId || ''}
                        onChange={(e) => onChange(index, 'cargoTypePriceId', e.target.value)}
                        required
                        aria-required="true"
                        disabled={readOnly}
                        className={!detail.cargoTypePriceId ? 'text-muted' : ''}
                    >
                        <option value="" disabled>-- Chọn loại hàng --</option>
                        {cargoTypes.map(ct => ct.cargoTypePriceId ? (
                            <option key={ct.cargoTypePriceId} value={ct.cargoTypePriceId} className="text-dark">
                                {ct.cargoTypeName} - {Number(ct.pricePerUnit).toLocaleString('vi-VN')} đ/{ct.unit}
                            </option>
                        ) : null)}
                    </Form.Select>
                </Form.Group>

                <Field
                    label="Số lượng"
                    type="number"
                    required
                    min="1"
                    placeholder="0"
                    disabled={readOnly}
                    value={detail.quantity ?? ''}
                    onChange={(e) => onChange(index, 'quantity', e.target.value)}
                />

                <Field
                    label="Trọng lượng"
                    type="number"
                    required
                    min="0.01"
                    step="any"
                    placeholder="0"
                    suffix="kg"
                    disabled={readOnly}
                    value={detail.weightKg ?? ''}
                    onChange={(e) => onChange(index, 'weightKg', e.target.value)}
                />

                <Field
                    label="Dài"
                    type="number"
                    min="0.01"
                    step="any"
                    placeholder="0"
                    suffix="m"
                    disabled={readOnly}
                    value={detail.length ?? ''}
                    onChange={(e) => handleDimensionChange('length', e.target.value)}
                />

                <Field
                    label="Rộng"
                    type="number"
                    min="0.01"
                    step="any"
                    placeholder="0"
                    suffix="m"
                    disabled={readOnly}
                    value={detail.width ?? ''}
                    onChange={(e) => handleDimensionChange('width', e.target.value)}
                />

                <Field
                    label="Cao"
                    type="number"
                    min="0.01"
                    step="any"
                    placeholder="0"
                    suffix="m"
                    disabled={readOnly}
                    value={detail.height ?? ''}
                    onChange={(e) => handleDimensionChange('height', e.target.value)}
                />

                <Field
                    label="Thể tích tự động"
                    type="number"
                    required
                    disabled
                    suffix="m³"
                    value={detail.dimensionVol ?? ''}
                />

                <Field
                    label="Giá"
                    type="text"
                    disabled
                    suffix="đ"
                    value={detail.calculatedPrice ? Number(detail.calculatedPrice).toLocaleString('vi-VN') : '0'}
                />
            </div>

            <Field
                label="Mô tả"
                as="textarea"
                rows={2}
                placeholder="Ghi chú thêm về hàng hóa này"
                disabled={readOnly}
                value={detail.description || ''}
                onChange={(e) => onChange(index, 'description', e.target.value)}
            />
        </div>
    );
}

export default function CargoTicketDetailSection({
    draftDetails, onAdd, onChange, onRemove, readOnly = false
}) {
    const { cargoTypes, setPageInfo } = useCargoTypes();
    const [armedDeleteIndex, setArmedDeleteIndex] = useState(null);

    useEffect(() => {
        setPageInfo(prev => ({ ...prev, size: 100 }));
    }, [setPageInfo]);

    return (
        <div className="mb-4">
            <div className="d-flex justify-content-between align-items-center mb-3">
                <h5 className="fw-bold mb-0">Chi tiết hàng hóa đính kèm</h5>
                {!readOnly && (
                    <Button className="d-flex align-items-center gap-2 custom-btn-general fw-medium" size="sm" onClick={onAdd}>
                        <BsPlus size={18} /> Đính kèm hàng hóa
                    </Button>
                )}
            </div>
            {readOnly && (
                <p className="text-muted small mb-3">
                    Đơn đã thanh toán — chi tiết hàng hóa bị khóa. Muốn đổi loại/số lượng/thể tích (ảnh hưởng cước), hãy hủy đơn và tạo đơn mới.
                </p>
            )}

            {draftDetails.length === 0 ? (
                <p className="text-muted mb-0">Chưa có chi tiết hàng hóa.</p>
            ) : (
                <div className="d-flex flex-column" style={{ gap: '12px' }}>
                    {draftDetails.map((detail, index) => (
                        <DetailItem
                            key={index}
                            detail={detail}
                            index={index}
                            cargoTypes={cargoTypes}
                            armedDeleteIndex={armedDeleteIndex}
                            setArmedDeleteIndex={setArmedDeleteIndex}
                            onChange={onChange}
                            onRemove={onRemove}
                            readOnly={readOnly}
                        />
                    ))}
                </div>
            )}
        </div>
    );
}
