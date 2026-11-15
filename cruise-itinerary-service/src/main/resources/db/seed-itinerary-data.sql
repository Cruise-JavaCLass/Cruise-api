-- =========================================================================
-- Seed Data for Cruise Itinerary Service (KAN-699)
-- Purpose: Pre-populates default cruise ships, ports, tour #1, daily
--          itineraries, stops, deck zones, and maritime policies.
-- =========================================================================

-- 1. Cruise Ships
INSERT INTO cruise_ships (
    id, name, code, operator, capacity, crew_capacity, total_cabins, total_decks,
    manufacturing_year, length_meters, beam_meters, draft_meters, status,
    description, amenities, image_url, active, created_at, updated_at
) VALUES (
    1, 'Grand Pearl Voyager', 'SHIP-001', 'Paradise Cruises', 2500, 800, 1200, 14,
    2022, 315.5, 38.0, 8.5, 'ACTIVE',
    'Flagship luxury cruise liner operating coastal Vietnam routes.',
    'Spa, Infinity Pool, Casino, Theater, Fitness Center, 5 Restaurants',
    'https://images.unsplash.com/photo-1548574505-5e239809ee19', true, NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

-- 2. Ports of Call
INSERT INTO ports (
    id, name, code, country, city, timezone, latitude, longitude,
    description, active, created_at, updated_at
) VALUES (
    1, 'Cảng Quốc Tế Hạ Long', 'VNHLG', 'Việt Nam', 'Quảng Ninh', 'Asia/Ho_Chi_Minh',
    20.9500000, 107.0300000, 'Cảng tàu khách quốc tế chuyên dụng tại Vịnh Hạ Long',
    true, NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO ports (
    id, name, code, country, city, timezone, latitude, longitude,
    description, active, created_at, updated_at
) VALUES (
    2, 'Cảng Tiên Sa Đà Nẵng', 'VNDAD', 'Việt Nam', 'Đà Nẵng', 'Asia/Ho_Chi_Minh',
    16.1200000, 108.2200000, 'Cảng biển nước sâu phục vụ tàu du lịch tại Đà Nẵng',
    true, NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

-- 3. Cruise Tour #1
INSERT INTO cruise_tours (
    id, name, code, description, ship_id, duration_days, duration_nights,
    departure_port, destination_port, start_date, end_date, max_capacity,
    base_price, currency, itinerary_overview, image_url, status, active,
    cancellation_reason, created_at, updated_at
) VALUES (
    1, 'Hải Trình Kỳ Quan Hạ Long - Đà Nẵng', 'TOUR-001',
    'Hải trình 3 ngày 2 đêm khám phá kỳ quan thiên nhiên miền Trung.',
    1, 3, 2, 'Cảng Quốc Tế Hạ Long', 'Cảng Tiên Sa Đà Nẵng',
    CURRENT_DATE + INTERVAL '7 day', CURRENT_DATE + INTERVAL '10 day', 2000,
    12500000.00, 'VND', 'Hạ Long -> Vịnh Bắc Bộ -> Đà Nẵng',
    'https://images.unsplash.com/photo-1507525428034-b723cf961d3e', 'PUBLISHED', true,
    NULL, NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

-- 4. Daily Itineraries for Tour #1
INSERT INTO daily_itineraries (
    id, tour_id, day_number, itinerary_date, title, description,
    port_id, port_name, arrival_time, departure_time, all_aboard_time,
    is_sea_day, weather_forecast, is_affected, change_reason, change_type,
    active, created_at, updated_at
) VALUES (
    1, 1, 1, CURRENT_DATE + INTERVAL '7 day', 'Khởi hành từ Cảng Hạ Long',
    'Làm thủ tục lên tàu, diễn tập cứu sinh SOLAS và thưởng ngoạn Vịnh Hạ Long.',
    1, 'Cảng Quốc Tế Hạ Long', '08:00:00', '12:00:00', '11:30:00',
    false, 'Nắng nhẹ, sóng êm', false, NULL, NULL,
    true, NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO daily_itineraries (
    id, tour_id, day_number, itinerary_date, title, description,
    port_id, port_name, arrival_time, departure_time, all_aboard_time,
    is_sea_day, weather_forecast, is_affected, change_reason, change_type,
    active, created_at, updated_at
) VALUES (
    2, 1, 2, CURRENT_DATE + INTERVAL '8 day', 'Hải trình trên Biển Đông',
    'Ngày trên biển với các hoạt động giải trí, dạ tiệc thuyền trưởng và spa thư giãn.',
    NULL, NULL, NULL, NULL, NULL,
    true, 'Trời quang mây tạnh', false, NULL, NULL,
    true, NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO daily_itineraries (
    id, tour_id, day_number, itinerary_date, title, description,
    port_id, port_name, arrival_time, departure_time, all_aboard_time,
    is_sea_day, weather_forecast, is_affected, change_reason, change_type,
    active, created_at, updated_at
) VALUES (
    3, 1, 3, CURRENT_DATE + INTERVAL '9 day', 'Cập cảng Tiên Sa Đà Nẵng',
    'Cập cảng, tham quan danh thắng Ngũ Hành Sơn và kết thúc hải trình.',
    2, 'Cảng Tiên Sa Đà Nẵng', '07:30:00', '16:00:00', '15:30:00',
    false, 'Nắng đẹp 28C', false, NULL, NULL,
    true, NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

-- 5. Cruise Stops for Tour #1
INSERT INTO cruise_stops (
    id, tour_id, port_id, stop_order, arrival_time, departure_time,
    all_aboard_time, description, active, created_at, updated_at
) VALUES (
    1, 1, 1, 1,
    (CURRENT_DATE + INTERVAL '7 day') + TIME '08:00:00',
    (CURRENT_DATE + INTERVAL '7 day') + TIME '12:00:00',
    (CURRENT_DATE + INTERVAL '7 day') + TIME '11:30:00',
    'Điểm khởi hành hành trình tại Hạ Long', true, NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO cruise_stops (
    id, tour_id, port_id, stop_order, arrival_time, departure_time,
    all_aboard_time, description, active, created_at, updated_at
) VALUES (
    2, 1, 2, 2,
    (CURRENT_DATE + INTERVAL '9 day') + TIME '07:30:00',
    (CURRENT_DATE + INTERVAL '9 day') + TIME '16:00:00',
    (CURRENT_DATE + INTERVAL '9 day') + TIME '15:30:00',
    'Điểm kết thúc hành trình tại Đà Nẵng', true, NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

-- 6. Ship Deck Zones for Ship #1
INSERT INTO ship_deck_zones (
    id, ship_id, deck_number, zone_name, zone_code, max_safety_capacity,
    zone_type, has_lifeboat_access, is_restricted, created_at, updated_at
) VALUES (
    1, 1, 5, 'Starlight Theater', 'DECK5-THEATER', 800, 'THEATER', false, false, NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO ship_deck_zones (
    id, ship_id, deck_number, zone_name, zone_code, max_safety_capacity,
    zone_type, has_lifeboat_access, is_restricted, created_at, updated_at
) VALUES (
    2, 1, 7, 'Main Pool Deck', 'DECK7-POOL', 450, 'POOL_DECK', true, false, NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

-- 7. System Cancellation Policies
INSERT INTO system_cancellation_policies (
    id, policy_name, service_type, hours_before_cutoff, refund_percentage,
    cancellation_fee, is_active, description, created_at, updated_at
) VALUES (
    1, 'Chính Sách Hủy Excursion Chuẩn 24H', 'EXCURSION', 24, 100, 0.00, true,
    'Hoàn tiền 100% nếu hủy trước 24 giờ', NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO system_cancellation_policies (
    id, policy_name, service_type, hours_before_cutoff, refund_percentage,
    cancellation_fee, is_active, description, created_at, updated_at
) VALUES (
    2, 'Chính Sách Hủy Tour Dài Ngày 72H', 'TOUR', 72, 80, 200000.00, true,
    'Hoàn 80% giá trị tour nếu hủy trước 72 giờ', NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;
