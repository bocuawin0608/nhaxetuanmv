/**
 * Format số tiền sang định dạng tiền tệ Việt Nam (VND)
 * VD: 100000 -> "100.000 ₫"
 */
export const formatCurrency = (value) => {
    if (value === null || value === undefined || isNaN(value) || value === '') {
        return '---';
    }
    return new Intl.NumberFormat('vi-VN', { 
        style: 'currency', 
        currency: 'VND' 
    }).format(value);
};

/**
 * Format ngày giờ sang định dạng DD/MM/YYYY HH:mm
 * VD: "2026-06-06T14:30:00Z" -> "06/06/2026 14:30"
 */
export const formatDateTime = (dateInput) => {
    if (!dateInput) return '---';
    
    const date = new Date(dateInput);
    if (isNaN(date.getTime())) return '---';

    const d = date.getDate().toString().padStart(2, '0');
    const m = (date.getMonth() + 1).toString().padStart(2, '0');
    const y = date.getFullYear();
    const h = date.getHours().toString().padStart(2, '0');
    const min = date.getMinutes().toString().padStart(2, '0');

    return `${d}/${m}/${y} ${h}:${min}`;
};

/**
 * Reliable rounding that avoids floating-point edge cases.
 * Shifts the decimal to the right, rounds the integer, then shifts it back.
 * @param {number} number  - The value to round.
 * @param {number} decimals - Number of decimal places.
 * @returns {number} The rounded number (not a string).
 */
export const safeRound = (number, decimals) =>
    Number(Math.round(number + 'e' + decimals) + 'e-' + decimals);

export {
    COACH_VALIDATION,
    validateAndFormatLicensePlate,
    normalizeSeatCodeInput,
    isValidSeatCode,
    getMinDateTime,
    isFutureDateTimeLocal,
} from './coachValidation';
