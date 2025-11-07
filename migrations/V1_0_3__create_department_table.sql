CREATE TABLE department (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    parent_department_id UUID,
    FOREIGN KEY (parent_department_id) REFERENCES department(id) ON DELETE SET NULL
);