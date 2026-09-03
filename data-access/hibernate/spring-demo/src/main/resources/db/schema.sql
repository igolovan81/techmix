CREATE TABLE IF NOT EXISTS author (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(200) NOT NULL
);

CREATE TABLE IF NOT EXISTS library_item (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_type VARCHAR(20) NOT NULL,
    title VARCHAR(300) NOT NULL,
    publication_year INT NOT NULL,
    isbn VARCHAR(20),
    region_code VARCHAR(10),
    runtime_minutes INT,
    issue_number VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS book_author (
    book_id BIGINT NOT NULL REFERENCES library_item(id),
    author_id BIGINT NOT NULL REFERENCES author(id),
    PRIMARY KEY (book_id, author_id)
);

CREATE TABLE IF NOT EXISTS copy (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    library_item_id BIGINT NOT NULL REFERENCES library_item(id),
    barcode VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS person (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    person_type VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    email VARCHAR(200) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS member (
    person_id BIGINT PRIMARY KEY REFERENCES person(id),
    membership_date DATE NOT NULL,
    street VARCHAR(200),
    city VARCHAR(100),
    zip_code VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS librarian (
    person_id BIGINT PRIMARY KEY REFERENCES person(id),
    employee_id VARCHAR(50) NOT NULL UNIQUE,
    department VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS loan (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    copy_id BIGINT NOT NULL REFERENCES copy(id),
    member_id BIGINT NOT NULL REFERENCES member(person_id),
    loan_date DATE NOT NULL,
    due_date DATE NOT NULL,
    return_date DATE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
