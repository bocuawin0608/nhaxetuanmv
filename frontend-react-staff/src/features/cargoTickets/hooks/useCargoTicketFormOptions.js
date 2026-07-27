import { useCallback, useEffect, useRef, useState } from 'react';
import { cargoTicketApi } from '../api/cargoTicketApi';
import { routeApi } from '../../routes/api/routeApi';

/**
 * @param {string|number} pickupStopId
 * @param {string|number} dropoffStopId
 * @param {{ loadTrips?: boolean }} [options] Create flow skips trip fetch (assignment deferred).
 */
export function useCargoTicketFormOptions(pickupStopId, dropoffStopId, options = {}) {
    const loadTrips = options.loadTrips !== false;
    const [formOptions, setFormOptions] = useState({
        trips: [],
        customers: [],
        stops: [],
        sellers: [],
        handlers: [],
        drivers: [],
        routes: [],
        agencyPickupStopId: null,
        agencyPickupStopName: null,
        agencyCity: null,
        defaultRouteId: null,
        defaultRouteName: null
    });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const latestTripRequest = useRef(0);
    const staticLoaded = useRef(false);

    const loadOptions = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            if (!staticLoaded.current) {
                const [data, routesData] = await Promise.all([
                    cargoTicketApi.getFormOptions(),
                    routeApi.getRoutesForDropdown()
                ]);
                setFormOptions(prev => ({
                    ...prev,
                    customers: data.customers ?? [],
                    stops: data.stops ?? [],
                    sellers: data.sellers ?? [],
                    handlers: data.handlers ?? [],
                    drivers: data.drivers ?? [],
                    routes: routesData ?? data.routes ?? [],
                    agencyPickupStopId: data.agencyPickupStopId ?? null,
                    agencyPickupStopName: data.agencyPickupStopName ?? null,
                    agencyCity: data.agencyCity ?? null,
                    defaultRouteId: data.defaultRouteId ?? null,
                    defaultRouteName: data.defaultRouteName ?? null
                }));
                staticLoaded.current = true;
            }

            const hasStops = pickupStopId && dropoffStopId && String(pickupStopId) !== String(dropoffStopId);
            if (loadTrips && hasStops) {
                const requestNumber = ++latestTripRequest.current;
                const tripsData = await cargoTicketApi.getTripsByStops({ pickupStopId, dropoffStopId });
                if (requestNumber === latestTripRequest.current) {
                    setFormOptions(prev => ({ ...prev, trips: tripsData }));
                }
            } else {
                setFormOptions(prev => ({ ...prev, trips: [] }));
            }
        } catch (requestError) {
            setError(requestError.response?.data?.message || 'Không thể tải dữ liệu danh mục.');
        } finally {
            setLoading(false);
        }
    }, [pickupStopId, dropoffStopId, loadTrips]);

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        loadOptions();
    }, [loadOptions]);

    return { ...formOptions, loading, error };
}
