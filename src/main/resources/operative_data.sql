INSERT INTO timesheets (employee_id, month, year, total_hours, status, created_at)
SELECT
    e.id,
    EXTRACT(MONTH FROM CURRENT_DATE),
    EXTRACT(YEAR FROM CURRENT_DATE),
    160.0,
    'DRAFT',
    CURRENT_TIMESTAMP
FROM employees e
WHERE e.termination_date IS NULL
ON CONFLICT (employee_id, month, year) DO NOTHING;

INSERT INTO timesheet_entries (timesheet_id, date, mark_type_id, hours_worked, created_at)
SELECT
    t.id,
    CURRENT_DATE - INTERVAL '1 day' * (s.day_index),
    mt.id,
    CASE
        WHEN EXTRACT(DOW FROM CURRENT_DATE - INTERVAL '1 day' * (s.day_index)) IN (0, 6) THEN 0.0
        ELSE 8.0
        END,
    CURRENT_TIMESTAMP
FROM timesheets t
         CROSS JOIN (SELECT generate_series(1, 10) as day_index) s
         CROSS JOIN mark_types mt
WHERE mt.code = 'Я'
  AND EXTRACT(MONTH FROM CURRENT_DATE - INTERVAL '1 day' * (s.day_index)) = EXTRACT(MONTH FROM CURRENT_DATE)
  AND EXTRACT(YEAR FROM CURRENT_DATE - INTERVAL '1 day' * (s.day_index)) = EXTRACT(YEAR FROM CURRENT_DATE)
ON CONFLICT DO NOTHING;