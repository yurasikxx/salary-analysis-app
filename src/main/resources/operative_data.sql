INSERT INTO timesheets (employee_id, month, year, total_hours, status)
VALUES (7, 9, 2025, 160, 'confirmed'),
       (8, 9, 2025, 168, 'confirmed'),
       (9, 9, 2025, 176, 'confirmed'),
       (10, 9, 2025, 160, 'draft')
ON CONFLICT DO NOTHING;

INSERT INTO payments (employee_id, month, year, payment_type_id, amount, description)
VALUES (7, 9, 2025, 1, 1800.00, 'Оклад за декабрь'),
       (8, 9, 2025, 1, 2000.00, 'Оклад за декабрь'),
       (9, 9, 2025, 1, 2200.00, 'Оклад за декабрь'),

       (7, 9, 2025, 2, 500.00, 'Премия ИТР за качественную работу'),

       (7, 9, 2025, 7, 234.00, 'Подоходный налог'),
       (8, 9, 2025, 7, 260.00, 'Подоходный налог')
ON CONFLICT DO NOTHING;