-- Khởi tạo database và phân chia schema cho các Microservices của Cruise Management System

CREATE SCHEMA IF NOT EXISTS auth_schema;
CREATE SCHEMA IF NOT EXISTS itinerary_schema;
CREATE SCHEMA IF NOT EXISTS activity_schema;
CREATE SCHEMA IF NOT EXISTS pos_schema;

GRANT ALL ON SCHEMA auth_schema TO cruise_admin;
GRANT ALL ON SCHEMA itinerary_schema TO cruise_admin;
GRANT ALL ON SCHEMA activity_schema TO cruise_admin;
GRANT ALL ON SCHEMA pos_schema TO cruise_admin;
