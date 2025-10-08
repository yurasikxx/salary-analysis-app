-- Создание таблицы подразделений
CREATE TABLE IF NOT EXISTS departments
(
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Создание таблицы должностей
CREATE TABLE IF NOT EXISTS positions
(
    id          SERIAL PRIMARY KEY,
    title       VARCHAR(100)   NOT NULL UNIQUE,
    base_salary NUMERIC(10, 2) NOT NULL CHECK (base_salary >= 0),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Создание таблицы сотрудников
CREATE TABLE IF NOT EXISTS employees
(
    id               SERIAL PRIMARY KEY,
    full_name        VARCHAR(200) NOT NULL,
    hire_date        DATE         NOT NULL,
    termination_date DATE         NULL,
    position_id      INTEGER      NOT NULL REFERENCES positions (id) ON DELETE RESTRICT,
    department_id    INTEGER      NOT NULL REFERENCES departments (id) ON DELETE RESTRICT,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Создание таблицы ролей пользователей
CREATE TABLE IF NOT EXISTS user_roles
(
    id   SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- Создание таблицы пользователей системы
CREATE TABLE IF NOT EXISTS users
(
    id            SERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    employee_id   INTEGER UNIQUE REFERENCES employees (id) ON DELETE CASCADE,
    role_id       INTEGER      NOT NULL REFERENCES user_roles (id) ON DELETE RESTRICT,
    is_active     BOOLEAN   DEFAULT TRUE,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Создание таблицы типов отметок (явка, отгул, больничный и т.д.)
CREATE TABLE IF NOT EXISTS mark_types
(
    id          SERIAL PRIMARY KEY,
    code        VARCHAR(10) NOT NULL UNIQUE,
    name        VARCHAR(50) NOT NULL,
    description TEXT
);

-- Создание таблицы видов оплат (начисления и удержания)
CREATE TABLE IF NOT EXISTS payment_types
(
    id          SERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    category    VARCHAR(20)  NOT NULL CHECK (category IN ('accrual', 'deduction')),
    description TEXT,
    formula     TEXT -- формула расчета (опционально)
);

-- Создание таблицы табелей (общая информация по периоду)
CREATE TABLE IF NOT EXISTS timesheets
(
    id           SERIAL PRIMARY KEY,
    employee_id  INTEGER NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    month        INTEGER NOT NULL CHECK (month >= 1 AND month <= 12),
    year         INTEGER NOT NULL CHECK (year >= 2020),
    total_hours  NUMERIC(6, 2) DEFAULT 0,
    status       VARCHAR(20)   DEFAULT 'draft' CHECK (status IN ('draft', 'confirmed')),
    confirmed_by INTEGER REFERENCES users (id),
    confirmed_at TIMESTAMP,
    created_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (employee_id, month, year)
);

-- Создание таблицы записей табеля (по дням)
CREATE TABLE IF NOT EXISTS timesheet_entries
(
    id           SERIAL PRIMARY KEY,
    timesheet_id INTEGER NOT NULL REFERENCES timesheets (id) ON DELETE CASCADE,
    date         DATE    NOT NULL,
    mark_type_id INTEGER NOT NULL REFERENCES mark_types (id) ON DELETE RESTRICT,
    hours_worked NUMERIC(4, 2) DEFAULT 0 CHECK (hours_worked >= 0 AND hours_worked <= 24),
    created_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

-- Создание таблицы операций по оплате (начисления и удержания)
CREATE TABLE IF NOT EXISTS payments
(
    id              SERIAL PRIMARY KEY,
    employee_id     INTEGER        NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    month           INTEGER        NOT NULL CHECK (month >= 1 AND month <= 12),
    year            INTEGER        NOT NULL CHECK (year >= 2020),
    payment_type_id INTEGER        NOT NULL REFERENCES payment_types (id) ON DELETE RESTRICT,
    amount          NUMERIC(10, 2) NOT NULL,
    description     TEXT,
    created_by      INTEGER REFERENCES users (id),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Создание таблицы итоговых расчетов зарплаты
CREATE TABLE IF NOT EXISTS salary_payments
(
    id               SERIAL PRIMARY KEY,
    employee_id      INTEGER NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    month            INTEGER NOT NULL CHECK (month >= 1 AND month <= 12),
    year             INTEGER NOT NULL CHECK (year >= 2020),
    total_accrued    NUMERIC(10, 2) DEFAULT 0,
    total_deducted   NUMERIC(10, 2) DEFAULT 0,
    net_salary       NUMERIC(10, 2) DEFAULT 0,
    calculation_date TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    status           VARCHAR(20)    DEFAULT 'calculated' CHECK (status IN ('calculated', 'paid')),
    UNIQUE (employee_id, month, year)
);

-- Создание индексов для оптимизации
CREATE INDEX IF NOT EXISTS idx_employees_department ON employees (department_id);
CREATE INDEX IF NOT EXISTS idx_employees_position ON employees (position_id);
CREATE INDEX IF NOT EXISTS idx_users_employee ON users (employee_id);
CREATE INDEX IF NOT EXISTS idx_timesheets_employee_period ON timesheets (employee_id, month, year);
CREATE INDEX IF NOT EXISTS idx_timesheet_entries_timesheet ON timesheet_entries (timesheet_id);
CREATE INDEX IF NOT EXISTS idx_payments_employee_period ON payments (employee_id, month, year);
CREATE INDEX IF NOT EXISTS idx_payments_type ON payments (payment_type_id);
CREATE INDEX IF NOT EXISTS idx_salary_payments_employee_period ON salary_payments (employee_id, month, year);