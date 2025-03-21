-- Communities table
CREATE TABLE communities (
    community_id VARCHAR(36) PRIMARY KEY,
    marketing_name VARCHAR(100) NOT NULL,
    property_id INT,
    property_address VARCHAR(255),
    property_zip VARCHAR(20)
);

-- FloorPlans table
CREATE TABLE floor_plans (
    floor_plan_unique_id VARCHAR(50) PRIMARY KEY,
    floor_plan_id VARCHAR(20) NOT NULL,
    property_id INT NOT NULL,
    floor_plan_name VARCHAR(50) NOT NULL,
    floor_plan_crm_id VARCHAR(50),
    floor_plan_path VARCHAR(255),
    floor_plan_sqft INT,
    floor_plan_bed INT,
    floor_plan_bath INT,
    floor_plan_deposit DECIMAL(10, 2),
    CONSTRAINT unique_property_floorplan UNIQUE (property_id, floor_plan_id)
);

-- FloorPlanGroups table
CREATE TABLE floor_plan_groups (
    group_id INT PRIMARY KEY AUTO_INCREMENT,
    group_type VARCHAR(50) NOT NULL
);

-- FloorPlanGroupMappings table (for many-to-many relationship)
CREATE TABLE floor_plan_group_mappings (
    id INT PRIMARY KEY AUTO_INCREMENT,
    group_id INT NOT NULL,
    floor_plan_unique_id VARCHAR(50) NOT NULL,
    FOREIGN KEY (group_id) REFERENCES floor_plan_groups(group_id),
    FOREIGN KEY (floor_plan_unique_id) REFERENCES floor_plans(floor_plan_unique_id)
);

-- Units table
CREATE TABLE units (
    unit_id VARCHAR(50) PRIMARY KEY,
    floor_plan_unique_id VARCHAR(50) NOT NULL,
    community_id VARCHAR(36) NOT NULL,
    unit_marketing_name VARCHAR(50),
    unit_crm_id VARCHAR(50),
    unit_floor INT,
    unit_sqft INT,
    unit_type_code VARCHAR(20),
    unit_type_name VARCHAR(50),
    building_number VARCHAR(10),
    unit_is_studio BOOLEAN DEFAULT FALSE,
    unit_has_discount BOOLEAN DEFAULT FALSE,
    featured_amenity VARCHAR(255),
    object_id VARCHAR(50),
    FOREIGN KEY (floor_plan_unique_id) REFERENCES floor_plans(floor_plan_unique_id),
    FOREIGN KEY (community_id) REFERENCES communities(community_id)
);

-- UnitAmenities table
CREATE TABLE unit_amenities (
    id INT PRIMARY KEY AUTO_INCREMENT,
    amenity_name VARCHAR(100) NOT NULL,
    UNIQUE (amenity_name)
);

-- UnitAmenityMappings table (for many-to-many relationship)
CREATE TABLE unit_amenity_mappings (
    id INT PRIMARY KEY AUTO_INCREMENT,
    unit_id VARCHAR(50) NOT NULL,
    amenity_id INT NOT NULL,
    FOREIGN KEY (unit_id) REFERENCES units(unit_id),
    FOREIGN KEY (amenity_id) REFERENCES unit_amenities(amenity_id)
);

-- LeasePrices table
CREATE TABLE lease_prices (
    id INT PRIMARY KEY AUTO_INCREMENT,
    unit_id VARCHAR(50) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    term INT NOT NULL,
    available_date DATE NOT NULL,
    date_timestamp BIGINT,
    is_earliest_available BOOLEAN DEFAULT FALSE,
    is_starting_price BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (unit_id) REFERENCES units(unit_id)
);

-- UnitIdGroupMappings table (for explicit unit_ids in groups)
CREATE TABLE unit_id_group_mappings (
    id INT PRIMARY KEY AUTO_INCREMENT,
    group_id INT NOT NULL,
    unit_id VARCHAR(50) NOT NULL,
    FOREIGN KEY (group_id) REFERENCES floor_plan_groups(group_id),
    FOREIGN KEY (unit_id) REFERENCES units(unit_id)
);