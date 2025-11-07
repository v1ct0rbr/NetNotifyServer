CREATE TABLE message_department (
    message_id UUID NOT NULL,
    department_id UUID NOT NULL,
    PRIMARY KEY (message_id, department_id),
    FOREIGN KEY (message_id) REFERENCES message(id) ON DELETE CASCADE,
    FOREIGN KEY (department_id) REFERENCES department(id) ON DELETE CASCADE
);