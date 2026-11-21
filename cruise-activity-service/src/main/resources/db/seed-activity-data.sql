-- =========================================================================
-- Seed Data for Cruise Activity and Excursion Service (KAN-700)
-- Purpose: Pre-populates default tour providers, onboard activities,
--          shore excursions, RFID blank inventory, SOLAS drill incidents,
--          and operational passenger notifications.
-- Serialized within a single transaction with SHARE ROW EXCLUSIVE table locks.
-- =========================================================================

BEGIN;

-- Lock tables against concurrent inserts before updating and resetting sequences
LOCK TABLE tour_providers, activities, shore_excursions, rfid_card_inventory, solas_muster_incidents, passenger_notifications IN SHARE ROW EXCLUSIVE MODE;

-- 1. Tour Providers
INSERT INTO tour_providers (
    id, code, name, tax_code, license_number, contact_person,
    email, phone, emergency_hotline, port_id, status, created_at, updated_at
)
SELECT
    gen_random_uuid(), 'PROV-001', 'Hạ Long Bay Marine Adventures', '0109988776', 'GP-LH-2022-001',
    'Nguyễn Văn Hùng', 'operations@halongmarine.vn', '+84901234567', '+84909999888',
    'VNHLG', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tour_providers WHERE code = 'PROV-001'
);

-- 2. Onboard Activities for Tour #1
INSERT INTO activities (
    tour_id, day_number, title, description, category, location,
    activity_date, start_time, end_time, max_capacity, current_participants,
    price, is_included, status, active, created_at, updated_at
)
SELECT
    1, 1, 'Captain''s Welcome Gala Dinner',
    'Exclusive evening reception with ship master and senior officers.',
    'Dining & Entertainment', 'Grand Ballroom - Deck 7',
    CURRENT_DATE + INTERVAL '1 day', '19:00:00'::TIME, '21:30:00'::TIME,
    250, 0, 0.00, true, 'SCHEDULED', true, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM activities WHERE tour_id = 1 AND title = 'Captain''s Welcome Gala Dinner'
);

INSERT INTO activities (
    tour_id, day_number, title, description, category, location,
    activity_date, start_time, end_time, max_capacity, current_participants,
    price, is_included, status, active, created_at, updated_at
)
SELECT
    1, 2, 'Sunset Acoustic Lounge & Cocktails',
    'Live jazz music and signature tropical cocktails on the open deck.',
    'Music & Lounge', 'Starlight Lounge - Deck 9',
    CURRENT_DATE + INTERVAL '2 day', '17:30:00'::TIME, '19:30:00'::TIME,
    120, 0, 150000.00, false, 'SCHEDULED', true, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM activities WHERE tour_id = 1 AND title = 'Sunset Acoustic Lounge & Cocktails'
);

-- 3. Shore Excursions for Tour #1
INSERT INTO shore_excursions (
    tour_id, port_id, day_number, title, description, port_name,
    meeting_point, local_provider, provider_contact, excursion_date,
    gathering_time, departure_time, return_to_ship_time, max_capacity,
    current_participants, price, child_price, currency, is_included,
    status, active, created_at, updated_at
)
SELECT
    1, 1, 1, 'Khám Phá Hang Sửng Sốt & Chèo Thuyền Kayak',
    'Tham quan hang động kỳ vĩ nhất Vịnh Hạ Long kết hợp chèo thuyền kayak tại Hang Luồn.',
    'Cảng Quốc Tế Hạ Long', 'Cầu tàu số 2 - Ga Quốc tế',
    'Hạ Long Bay Marine Adventures', '+84901234567',
    CURRENT_DATE + INTERVAL '1 day', '08:30:00'::TIME, '09:00:00'::TIME, '13:00:00'::TIME,
    80, 0, 850000.00, 450000.00, 'VND', false,
    'OPEN_FOR_BOOKING', true, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM shore_excursions WHERE tour_id = 1 AND title = 'Khám Phá Hang Sửng Sốt & Chèo Thuyền Kayak'
);

-- 4. Blank RFID Keycards
INSERT INTO rfid_card_inventory (
    card_uid, card_type, batch_number, status, active, created_at, updated_at
)
SELECT
    'RFID-UID-000' || n, 'MIFARE_CLASSIC_1K', 'BATCH-2026-Q1-001', 'AVAILABLE', true, NOW(), NOW()
FROM generate_series(1, 9) AS n
WHERE NOT EXISTS (
    SELECT 1 FROM rfid_card_inventory WHERE card_uid = 'RFID-UID-000' || n
);

-- 5. SOLAS Drill Muster Incident
INSERT INTO solas_muster_incidents (
    incident_code, emergency_type, status, triggered_by, announcement_text,
    acoustic_signal_code, total_souls_on_board, mustered_souls_count,
    triggered_at, active, created_at, updated_at
)
SELECT
    'SOLAS-DRILL-001', 'GENERAL_EMERGENCY', 'DRILL_IN_PROGRESS', 'SAFETY_OFFICER',
    'Diễn tập an toàn cứu sinh SOLAS bắt buộc trước giờ tàu xuất bến.',
    '7_SHORT_1_LONG', 100, 0, NOW(), true, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM solas_muster_incidents WHERE incident_code = 'SOLAS-DRILL-001'
);

-- 6. Passenger Operational Notifications
INSERT INTO passenger_notifications (
    passenger_id, title, message, category, action_url, is_read, active, created_at, updated_at
)
SELECT
    1, 'Chào mừng Quý khách lên tàu Grand Pearl Voyager',
    'Hành trình khám phá Hạ Long - Đà Nẵng đã bắt đầu. Xin vui lòng tham dự diễn tập an toàn SOLAS lúc 10:00.',
    'OPERATIONAL', '/portal/itinerary', false, true, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM passenger_notifications WHERE passenger_id = 1 AND title = 'Chào mừng Quý khách lên tàu Grand Pearl Voyager'
);

-- 7. Advance Identity Sequences
SELECT setval(pg_get_serial_sequence('activities', 'id'), COALESCE((SELECT MAX(id) FROM activities), 1));
SELECT setval(pg_get_serial_sequence('shore_excursions', 'id'), COALESCE((SELECT MAX(id) FROM shore_excursions), 1));
SELECT setval(pg_get_serial_sequence('rfid_card_inventory', 'id'), COALESCE((SELECT MAX(id) FROM rfid_card_inventory), 1));
SELECT setval(pg_get_serial_sequence('solas_muster_incidents', 'id'), COALESCE((SELECT MAX(id) FROM solas_muster_incidents), 1));
SELECT setval(pg_get_serial_sequence('passenger_notifications', 'id'), COALESCE((SELECT MAX(id) FROM passenger_notifications), 1));

COMMIT;
