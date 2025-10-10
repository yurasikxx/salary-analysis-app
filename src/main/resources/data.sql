INSERT INTO user_roles (name) VALUES
                                  ('ADMIN'),
                                  ('HR'),
                                  ('RATESETTER'),
                                  ('ACCOUNTANT'),
                                  ('ANALYST')
ON CONFLICT (name) DO NOTHING;

INSERT INTO employees (full_name, hire_date, position_id, department_id) VALUES
('Петрова Анна Сергеевна', '2022-03-10', 1, 1),
('Кочерга Людмила Ивановна', '2023-01-15', 1, 1),

('Сидоров Алексей Петрович', '2021-11-15', 2, 2),
('Ковалева Ирина Васильевна', '2020-06-20', 2, 2),

('Никитина Ольга Дмитриевна', '2019-08-12', 3, 3),
('Иванова Екатерина Максимовна', '2023-01-25', 4, 3)
ON CONFLICT DO NOTHING;

INSERT INTO users (username, password_hash, employee_id, role_id, is_active) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVwUi.', NULL, 1, true),  -- password: admin123

('petrova.a', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVwUi.', 1, 2, true), -- password: user123
('kocherga.l', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVwUi.', 2, 2, true),

('sidorov.a', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVwUi.', 3, 3, true),
('kovaleva.i', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVwUi.', 4, 3, true),

('nikitina.o', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVwUi.', 5, 4, true),
('ivanova.e', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVwUi.', 6, 4, true)
ON CONFLICT (username) DO NOTHING;