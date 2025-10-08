-- Добавим тестовые табели за текущий месяц
INSERT INTO timesheets (employee_id, month, year, total_hours, status)
VALUES (1, 9, 2025, 160, 'confirmed'),
       (2, 9, 2025, 168, 'confirmed'),
       (6, 9, 2025, 176, 'confirmed'),
       (11, 9, 2025, 160, 'draft')
ON CONFLICT DO NOTHING;

-- Тестовые операции по оплате
INSERT INTO payments (employee_id, month, year, payment_type_id, amount, description)
VALUES
-- Оклады
(1, 9, 2025, 1, 1800.00, 'Оклад за декабрь'),
(2, 9, 2025, 1, 2000.00, 'Оклад за декабрь'),
(6, 9, 2025, 1, 2200.00, 'Оклад за декабрь'),

-- Премии ИТР
(6, 9, 2025, 2, 500.00, 'Премия ИТР за качественную работу'),

-- Подоходный налог (13%)
(1, 9, 2025, 7, 234.00, 'Подоходный налог'),
(2, 9, 2025, 7, 260.00, 'Подоходный налог')
ON CONFLICT DO NOTHING;