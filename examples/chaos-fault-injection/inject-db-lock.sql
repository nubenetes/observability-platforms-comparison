-- ==============================================================================
-- Script: inject-db-lock.sql
-- Purpose: Injects an exclusive table lock into Microsoft SQL Server
-- Target: Database Monitoring (DBM), Wait Stats, APM Query Spans (Pillars 1, 3, 7)
-- ==============================================================================

USE EnterpriseCore;
GO

PRINT '===================================================================';
PRINT ' [CHAOS TEST] Initiating Exclusive Table Lock on enterprise_orders ';
PRINT '===================================================================';

-- Ensure table exists
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'enterprise_orders')
BEGIN
    CREATE TABLE enterprise_orders (
        order_id VARCHAR(64) PRIMARY KEY,
        amount DECIMAL(18, 2),
        status VARCHAR(32),
        created_at DATETIME2 DEFAULT CURRENT_TIMESTAMP
    );
    INSERT INTO enterprise_orders (order_id, amount, status) VALUES ('INIT-001', 100.00, 'CONFIRMED');
END
GO

-- Open an uncommitted transaction holding an exclusive table lock
BEGIN TRANSACTION;

UPDATE enterprise_orders WITH (TABLOCKX)
SET status = 'LOCKED_BY_FAULT_INJECTION'
WHERE order_id = 'INIT-001';

PRINT 'Exclusive table lock acquired (SPID: ' + CAST(@@SPID AS VARCHAR(10)) + ').';
PRINT 'All concurrent microservice INSERTs and SELECTs will be blocked in LCK_M_X.';
PRINT 'Holding lock for 90 seconds...';

WAITFOR DELAY '00:01:30';

ROLLBACK TRANSACTION;
PRINT 'Transaction rolled back. Locks released.';
GO

-- Diagnostic DMV Query to verify lock impact during execution
SELECT 
    r.session_id,
    r.status,
    r.blocking_session_id,
    r.wait_type,
    r.wait_time AS wait_time_ms,
    r.last_wait_type,
    t.text AS sql_statement
FROM sys.dm_exec_requests r
CROSS APPLY sys.dm_exec_sql_text(r.sql_handle) t
WHERE r.blocking_session_id <> 0;
GO
