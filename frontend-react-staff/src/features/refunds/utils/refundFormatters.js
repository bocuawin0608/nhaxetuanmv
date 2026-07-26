const REFUND_STATUS_LABELS = {
    PENDING: 'Chờ xử lý',
    COMPLETED: 'Đã hoàn',
    FAILED: 'Thất bại',
};

const REFUND_METHOD_LABELS = {
    BANK_TRANSFER: 'Chuyển khoản',
    CASH: 'Tiền mặt',
};

const ALLOWED_REFUND_STATUSES = ['PENDING', 'COMPLETED', 'FAILED', ''];

const BANK_TRANSFER_TRANSACTION_PATTERN = /^[A-Za-z0-9-]{4,100}$/;

export const DEFAULT_REFUND_STATUS = 'PENDING';
export const DEFAULT_TAB = 'passenger';

export const EMPTY_REFUND_FILTERS = {
    status: DEFAULT_REFUND_STATUS,
    ticketCode: '',
    phone: '',
    createdFrom: '',
    createdTo: '',
};

export function formatRefundStatus(status) {
    return REFUND_STATUS_LABELS[status] || status || '—';
}

export function formatRefundMethod(method) {
    return REFUND_METHOD_LABELS[method] || method || '—';
}

export function formatDateTime(value) {
    if (!value) return '—';
    return new Date(value).toLocaleString('vi-VN', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    });
}

export function formatCurrency(value) {
    if (value == null || Number.isNaN(Number(value))) return '—';
    return `${Number(value).toLocaleString('vi-VN')} đ`;
}

export function truncateText(value, maxLength = 80) {
    if (!value) return '—';
    const trimmed = value.trim();
    if (trimmed.length <= maxLength) return trimmed;
    return `${trimmed.slice(0, maxLength)}…`;
}

export function parseRefundFiltersFromSearchParams(searchParams) {
    if (!searchParams.has('status')) {
        return {
            ...EMPTY_REFUND_FILTERS,
            ticketCode: searchParams.get('ticketCode') || '',
            phone: searchParams.get('phone') || '',
            createdFrom: searchParams.get('createdFrom') || '',
            createdTo: searchParams.get('createdTo') || '',
        };
    }

    const rawStatus = searchParams.get('status');
    const status = rawStatus === 'ALL'
        ? ''
        : (ALLOWED_REFUND_STATUSES.includes(rawStatus) ? rawStatus : DEFAULT_REFUND_STATUS);

    return {
        status,
        ticketCode: searchParams.get('ticketCode') || '',
        phone: searchParams.get('phone') || '',
        createdFrom: searchParams.get('createdFrom') || '',
        createdTo: searchParams.get('createdTo') || '',
    };
}

export function parseRefundTab(searchParams) {
    const tab = searchParams.get('tab') || DEFAULT_TAB;
    return tab === 'cargo' ? 'cargo' : DEFAULT_TAB;
}

export function parsePageInfo(searchParams) {
    const page = Number(searchParams.get('page') || 0);
    const size = Number(searchParams.get('size') || 20);

    return {
        page: Number.isInteger(page) && page >= 0 ? page : 0,
        size: Number.isInteger(size) && size >= 1 && size <= 100 ? size : 20,
    };
}

export function buildRefundListQueryParams(filters, { tab = DEFAULT_TAB, page = 0, size = 20 } = {}) {
    const params = new URLSearchParams();

    params.set('tab', tab);
    if (filters.status) {
        params.set('status', filters.status);
    } else {
        params.set('status', 'ALL');
    }

    const ticketCode = filters.ticketCode?.trim();
    const phone = filters.phone?.trim();
    if (ticketCode) params.set('ticketCode', ticketCode);
    if (phone) params.set('phone', phone);
    if (filters.createdFrom) params.set('createdFrom', filters.createdFrom);
    if (filters.createdTo) params.set('createdTo', filters.createdTo);
    if (page > 0) params.set('page', String(page));
    if (size !== 20) params.set('size', String(size));

    return params;
}

export function buildRefundSearchApiParams(filters, pageInfo) {
    const params = {
        page: pageInfo.page,
        size: pageInfo.size,
    };

    if (filters.status) params.status = filters.status;

    const ticketCode = filters.ticketCode?.trim();
    const phone = filters.phone?.trim();
    if (ticketCode) params.ticketCode = ticketCode;
    if (phone) params.phone = phone;
    if (filters.createdFrom) params.createdFrom = filters.createdFrom;
    if (filters.createdTo) params.createdTo = filters.createdTo;

    return params;
}

export function validateRefundDateRange(createdFrom, createdTo) {
    if (!createdFrom || !createdTo) return null;
    if (createdFrom > createdTo) {
        return 'Ngày bắt đầu không được lớn hơn ngày kết thúc.';
    }
    return null;
}

const TICKET_CODE_PATTERN = /^[A-Za-z0-9_-]{3,64}$/;

function normalizePhoneForSearch(rawPhone) {
    if (!rawPhone) return '';
    const trimmed = rawPhone.trim();
    if (trimmed.startsWith('+84')) {
        return `0${trimmed.slice(3)}`;
    }
    if (trimmed.startsWith('84') && trimmed.length === 11) {
        return `0${trimmed.slice(2)}`;
    }
    return trimmed;
}

/**
 * Validates refund search filters before calling the API.
 * Returns a user-facing error message, or null when valid.
 */
export function validateRefundSearchFilters(filters) {
    const ticketCode = filters.ticketCode?.trim();
    if (ticketCode && !TICKET_CODE_PATTERN.test(ticketCode)) {
        return 'Mã vé phải gồm từ 3 đến 64 ký tự chữ, số, gạch dưới hoặc gạch ngang.';
    }

    const phone = filters.phone?.trim();
    if (phone) {
        const normalizedPhone = normalizePhoneForSearch(phone);
        if (!/^[0-9]{3,11}$/.test(normalizedPhone)) {
            return 'Số điện thoại phải gồm từ 3 đến 11 chữ số.';
        }
    }

    const allowedStatuses = ['', 'PENDING', 'COMPLETED', 'FAILED'];
    if (filters.status && !allowedStatuses.includes(filters.status)) {
        return 'Trạng thái hoàn tiền không hợp lệ.';
    }

    return validateRefundDateRange(filters.createdFrom, filters.createdTo);
}

export function isPositiveInteger(value) {
    return Number.isInteger(value) && value > 0;
}

export function getTransactionIdConfig(refundMethod) {
    if (refundMethod === 'BANK_TRANSFER') {
        return {
            label: 'Mã giao dịch chuyển khoản',
            placeholder: 'Nhập mã giao dịch chuyển khoản',
            required: true,
            pattern: BANK_TRANSFER_TRANSACTION_PATTERN,
            invalidMessage: 'Mã giao dịch phải gồm 4-100 ký tự chữ, số hoặc dấu gạch ngang.',
        };
    }

    return {
        label: 'Ghi chú / biên nhận',
        placeholder: 'Tuỳ chọn',
        required: false,
        pattern: null,
        maxLength: 100,
        invalidMessage: 'Ghi chú không được vượt quá 100 ký tự.',
    };
}

export function validateTransactionId(refundMethod, rawValue) {
    const config = getTransactionIdConfig(refundMethod);
    const trimmed = rawValue?.trim() || '';

    if (config.required && !trimmed) {
        return 'Vui lòng nhập thông tin xác nhận thanh toán.';
    }

    if (!trimmed) {
        return null;
    }

    if (config.maxLength && trimmed.length > config.maxLength) {
        return config.invalidMessage;
    }

    if (config.pattern && !config.pattern.test(trimmed)) {
        return config.invalidMessage;
    }

    return null;
}
