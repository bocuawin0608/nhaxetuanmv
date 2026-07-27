import axiosClient from '../../../api/axiosClient';

export const tripService = {
    /**
     * Loads active backend routes so the public trip form can build searchable
     * departure and destination dropdowns from real route data.
     */
    getRouteDropdown: async () => {
        return axiosClient.get('/v1/routes/dropdown', { skipAuth: true });
    },

    /**
     * Loads customer-search locations from the dedicated route projection.
     * Existing route dropdown consumers keep their original API contract.
     */
    getCustomerRouteLocations: async () => {
        return axiosClient.get('/v1/routes/customer-locations', { skipAuth: true });
    },

    /**
     * Loads the ordered stop timeline for the concrete trip selected by the customer.
     */
    getTripStops: async (tripId) => {
        return axiosClient.get(`/v1/trips/${tripId}/stops`, { skipAuth: true });
    },

    /**
     * Searches customer trips for the selected route/date and optional filters.
     */
    searchTrips: async (searchParams) => {
        try {
            const params = {
                date: searchParams.date,
                route: searchParams.route,
                page: searchParams.page ?? 0,
                size: searchParams.size ?? 10
            };

            if (searchParams.isAdvanced) {
                params.advanced = 'true';
                if (searchParams.timeSlots) params.timeSlots = searchParams.timeSlots;
                if (searchParams.layouts) params.layouts = searchParams.layouts;
                if (searchParams.minPrice !== undefined && searchParams.minPrice !== null) params.minPrice = searchParams.minPrice;
                if (searchParams.maxPrice !== undefined && searchParams.maxPrice !== null) params.maxPrice = searchParams.maxPrice;
            }

            const responseData = await axiosClient.get('/v1/trips/home', {
                params,
                skipAuth: true
            });
            return responseData;
        } catch (error) {
            console.error("Lỗi nghẽn mạch tại tầng Trip Service:", error);
            throw error;
        }
    }
};
