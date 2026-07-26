USE VeXeDB;
GO

SET NOCOUNT ON;
PRINT N'=== BẮT ĐẦU SEED fakedata1 (BUSINESS-ALIGNED) ===';
-- Trip density/rules align with sp_AutoGenerateWeeklySchedule_Final (Procedure.sql):
-- 24 departures/day/direction, 720-min buffer, go-return route flip, 150 reserve coaches.
-- Cargo orders (cargo_ticket) are intentionally not seeded — create via app flows.

-- ============================================================================
-- CLEANUP (FK-safe order)
-- ============================================================================
PRINT N'-> Đang dọn dẹp dữ liệu cũ...';
DELETE FROM [refund];
DELETE FROM [accompanied_child];
DELETE FROM [payment];
DELETE FROM [cargo_ticket_detail];
DELETE FROM [passenger_ticket_detail];
DELETE FROM [cargo_ticket];
DELETE FROM [passenger_ticket];
DELETE FROM [trip_seat];
DELETE FROM [trip];
DELETE FROM [seat];
DELETE FROM [coach_status_log];
DELETE FROM [coach];
DELETE FROM [cargo_type_price];
DELETE FROM [coach_type_price];
DELETE FROM [route_stop];
DELETE FROM [staff];
DELETE FROM [ticket_agency];
DELETE FROM [customer];
DELETE FROM [account_role];
DELETE FROM [refresh_token];
DELETE FROM [cargo_type];
DELETE FROM [coach_type];
DELETE FROM [route];
DELETE FROM [coach_stop];
DELETE FROM [voucher];
DELETE FROM [role];
DELETE FROM [account];

PRINT N'-> Đang reset Identity...';
DECLARE @TableName NVARCHAR(256);
DECLARE @SQL NVARCHAR(MAX);

DECLARE cur CURSOR FOR
SELECT QUOTENAME(s.name) + '.' + QUOTENAME(t.name)
FROM sys.tables t
JOIN sys.schemas s ON t.schema_id = s.schema_id
JOIN sys.identity_columns ic ON t.object_id = ic.object_id;

OPEN cur;
FETCH NEXT FROM cur INTO @TableName;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @SQL = N'
    IF EXISTS (SELECT 1 FROM sys.identity_columns WHERE object_id = OBJECT_ID(''' + @TableName + ''') AND last_value IS NOT NULL)
        DBCC CHECKIDENT (''' + @TableName + ''', RESEED, 0);';
    EXEC sp_executesql @SQL;
    FETCH NEXT FROM cur INTO @TableName;
END;
CLOSE cur;
DEALLOCATE cur;

DECLARE @StaffPwd VARCHAR(255) = '$2a$10$G1TCgI4zgHQpN1hyuRMEaOOvGeoSg7MCMQDapcuLl0NsIZNn104w2'; -- bcrypt("123456")

-- ============================================================================
-- LEVEL 1: STRONG ENTITIES
-- ============================================================================
PRINT N'-> Nạp role / voucher / coach_stop / route / coach_type / cargo_type...';

INSERT INTO [role] (roleName) VALUES
(N'ADMIN'), (N'MANAGER'), (N'TICKET_STAFF'), (N'TRIP_STAFF'), (N'CUSTOMER');

INSERT INTO [voucher] (voucherCode, discountValue, startEffectiveDate, endEffectiveDate, discountType, maxDiscountValue, minOrderValue, usageLimit) VALUES
('HE2026', 10.00, '2026-01-01', '2028-12-31', 'PERCENT', 50000.00, 200000.00, 1000),
('GIAM50K', 50000.00, '2026-01-01', '2028-12-31', 'FIXED', 50000.00, 0.00, 1000),
('TET2026', 15.00, '2026-01-01', '2026-03-01', 'PERCENT', 100000.00, 300000.00, 500);

-- Coach stops: Hà Nội + Quảng Trị (former Quảng Bình addresses only). City label = Quảng Trị.
-- Address must match: ^[^,]+(,\s+[^,]+)+$
INSERT INTO [coach_stop] (stopPointName, address, city, surcharge, isActive, latitude, longitude) VALUES
-- Hà Nội
(N'Bến Xe Nước Ngầm',           N'Số 1 Ngọc Hồi, Hoàng Mai',                      N'Hà Nội',     0.00,      1, 20.9399174370000000, 105.8442251250000000),
(N'Bến Xe Giáp Bát',            N'Số 6 Giải Phóng, Hoàng Mai',                    N'Hà Nội',     0.00,      1, 20.9782000000000000, 105.8411000000000000),
(N'Bến Xe Mỹ Đình',             N'Số 20 Phạm Hùng, Nam Từ Liêm',                  N'Hà Nội',     0.00,      1, 21.0285000000000000, 105.7782000000000000),
(N'Sảnh T1+T2 - Sân bay Nội Bài', N'Sảnh E, Nhà ga T1, Sóc Sơn',                  N'Hà Nội',     100000.00,	1, 21.2149337000000000, 105.8007099000000000),
(N'Văn phòng Cầu Giấy',         N'Số 125 Xuân Thủy, Cầu Giấy',                    N'Hà Nội',     0.00,		1, 21.0368000000000000, 105.7825000000000000),
-- Quảng Trị (physical boundary = former Quảng Bình)
(N'Trạm Dừng Ba Đồn',           N'Phường Ba Đồn, Thị xã Ba Đồn',                  N'Quảng Trị',  0.00,      1, 17.7546377497501000, 106.4233121072500000),
(N'Trạm Dừng Quảng Ninh',       N'Thị trấn Hoàn Lão, Huyện Quảng Ninh',           N'Quảng Trị',  0.00,      1, 17.6485000000000000, 106.5082000000000000),
(N'Văn Phòng Đồng Hới',         N'Số 58 Trần Hưng Đạo, Đồng Hới',                 N'Quảng Trị',  0.00,      1, 17.4691879169492000, 106.6104383894200000),
(N'Bến Xe Đồng Hới',            N'Số 95 Lý Thường Kiệt, Đồng Hới',                N'Quảng Trị',  0.00,      1, 17.4682000000000000, 106.6221000000000000),
(N'Trạm Dừng Phong Nha',        N'Thị trấn Phong Nha, Huyện Bố Trạch',            N'Quảng Trị',  0.00,		1, 17.5904000000000000, 106.2831000000000000),
(N'Trạm Dừng Lệ Thủy',          N'Quốc Lộ 1A, Huyện Lệ Thủy',                     N'Quảng Trị',  0.00,      1, 17.2425945873282000, 106.8147884762850000),
(N'Văn phòng Kiến Giang',       N'Thị trấn Kiến Giang, Huyện Lệ Thủy',            N'Quảng Trị',  0.00,      1, 17.2231000000000000, 106.7915000000000000);

INSERT INTO [route] (routeName, totalKilometers, totalMinutes) VALUES
(N'Hà Nội - Quảng Trị', 530.00, 640),
(N'Quảng Trị - Hà Nội', 530.00, 640);

INSERT INTO [coach_type] (coachTypeName, totalSeat, seatLayout) VALUES
(N'Xe Limousine VIP 20 phòng', 20,
 N'{"totalFloors":2,"rows":5,"cols":2,"floors":[[["SEAT","SEAT"],["SEAT","SEAT"],["SEAT","SEAT"],["SEAT","SEAT"],["SEAT","SEAT"]],[["SEAT","SEAT"],["SEAT","SEAT"],["SEAT","SEAT"],["SEAT","SEAT"],["SEAT","SEAT"]]]}'),
(N'Xe Giường Nằm Luxury 32 chỗ', 32,
 N'{"totalFloors":2,"rows":6,"cols":3,"floors":[[["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["EMPTY","SEAT","EMPTY"]],[["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["EMPTY","SEAT","EMPTY"]]]}'),
(N'Xe Khách Truyền Thống 38 chỗ', 38,
 N'{"totalFloors":2,"rows":7,"cols":3,"floors":[[["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["EMPTY","SEAT","EMPTY"]],[["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["SEAT","SEAT","SEAT"],["EMPTY","SEAT","EMPTY"]]]}');

-- Cargo types: name only; surcharge lives in cargo_type_price (unit + pricePerUnit)
INSERT INTO [cargo_type] (cargoTypeName) VALUES
(N'Hàng khô / Thùng Carton'),
(N'Xe máy / Xe điện'),
(N'Hàng dễ vỡ'),
(N'Hàng đông lạnh / thực phẩm'),
(N'Đồ gia dụng cồng kềnh'),
(N'Linh kiện điện tử'),
(N'Nông sản / trái cây'),
(N'Hàng quá khổ đặc biệt');

-- ============================================================================
-- LEVEL 2: TICKET AGENCIES (1 agency per coach stop) + PERSONNEL
-- ============================================================================
PRINT N'-> Tạo đại lý 1:1 với từng điểm dừng...';

INSERT INTO [ticket_agency] (stopPointId, ticketAgencyName) VALUES
(1,  N'Đại lý Bến Xe Nước Ngầm'),
(2,  N'Đại lý Bến Xe Giáp Bát'),
(3,  N'Đại lý Bến Xe Mỹ Đình'),
(4,  N'Đại lý Sân Bay Nội Bài'),
(5,  N'Đại lý Văn phòng Cầu Giấy'),
(6,  N'Đại lý Ba Đồn'),
(7,  N'Đại lý Quảng Ninh'),
(8,  N'Đại lý Văn Phòng Đồng Hới'),
(9,  N'Đại lý Bến Xe Đồng Hới'),
(10, N'Đại lý Phong Nha'),
(11, N'Đại lý Lệ Thủy'),
(12, N'Đại lý Kiến Giang');

DECLARE @AgencyTable TABLE (RowIdx INT IDENTITY(1,1), AgencyId INT);
INSERT INTO @AgencyTable (AgencyId) SELECT ticketAgencyId FROM [ticket_agency] ORDER BY ticketAgencyId;
DECLARE @TotalAgencies INT = (SELECT COUNT(*) FROM @AgencyTable);

PRINT N'-> Khởi tạo account + staff / customer...';

DECLARE @IdOutput TABLE (Id INT);
DECLARE @GeneratedId INT;
DECLARE @idx INT = 1;
DECLARE @phoneStr VARCHAR(20);
DECLARE @dobStr DATE;
DECLARE @cccdStr VARCHAR(20);
DECLARE @emailStr VARCHAR(100);
DECLARE @firebaseUid VARCHAR(128);

-- Admin (local)
INSERT INTO [account] (username, passwordHash, firebaseUid, authProvider, isActive)
OUTPUT inserted.accountId INTO @IdOutput
VALUES ('0901111111', @StaffPwd, NULL, 'local', 1);
SELECT TOP 1 @GeneratedId = Id FROM @IdOutput; DELETE FROM @IdOutput;
INSERT INTO [account_role] (accountId, roleId) VALUES (@GeneratedId, 1);
INSERT INTO [staff] (accountId, ticketAgencyId, staffName, phone, email, dob, cccd, staffPosition, hireDate)
VALUES (@GeneratedId, NULL, N'Hệ Thống Admin', '0901111111', 'admin.root@vexedb.vn', '1990-05-15', '030090000001', 'MANAGER', '2024-01-01');

-- Manager (local)
INSERT INTO [account] (username, passwordHash, firebaseUid, authProvider, isActive)
OUTPUT inserted.accountId INTO @IdOutput
VALUES ('0902222222', @StaffPwd, NULL, 'local', 1);
SELECT TOP 1 @GeneratedId = Id FROM @IdOutput; DELETE FROM @IdOutput;
INSERT INTO [account_role] (accountId, roleId) VALUES (@GeneratedId, 2);
INSERT INTO [staff] (accountId, ticketAgencyId, staffName, phone, email, dob, cccd, staffPosition, hireDate)
VALUES (@GeneratedId, 1, N'Quản Lý Trưởng', '0902222222', 'quanlytruong@vexedb.vn', '1985-10-20', '030085000002', 'MANAGER', '2024-01-01');

-- 30 Ticket Staff (local) — round-robin across all agencies
SET @idx = 1;
WHILE @idx <= 30
BEGIN
    SET @phoneStr = '0931000' + RIGHT('00' + CAST(@idx AS VARCHAR(2)), 2);
    SET @dobStr = CAST((1990 + (@idx % 12)) AS VARCHAR(4)) + '-03-' + RIGHT('0' + CAST((10 + (@idx % 18)) AS VARCHAR(2)), 2);
    SET @cccdStr = '03009' + CAST((1 + (@idx % 9)) AS VARCHAR(1)) + '000' + RIGHT('00' + CAST(@idx AS VARCHAR(2)), 2);
    SET @emailStr = 'ticketstaff' + CAST(@idx AS VARCHAR(2)) + '@vexedb.vn';

    INSERT INTO [account] (username, passwordHash, firebaseUid, authProvider, isActive)
    OUTPUT inserted.accountId INTO @IdOutput
    VALUES (@phoneStr, @StaffPwd, NULL, 'local', 1);
    SELECT TOP 1 @GeneratedId = Id FROM @IdOutput; DELETE FROM @IdOutput;

    DECLARE @TargetAgencyId INT;
    SELECT @TargetAgencyId = AgencyId FROM @AgencyTable WHERE RowIdx = ((@idx % @TotalAgencies) + 1);

    INSERT INTO [account_role] (accountId, roleId) VALUES (@GeneratedId, 3);
    INSERT INTO [staff] (accountId, ticketAgencyId, staffName, phone, email, dob, cccd, staffPosition, hireDate)
    VALUES (@GeneratedId, @TargetAgencyId, N'NV Bán Vé ' + CAST(@idx AS NVARCHAR(5)), @phoneStr, @emailStr, @dobStr, @cccdStr, 'TICKET_STAFF', '2025-01-01');
    SET @idx = @idx + 1;
END;

-- 80 Drivers (local, TRIP_STAFF role, no agency)
SET @idx = 1;
WHILE @idx <= 80
BEGIN
    SET @phoneStr = '0932000' + RIGHT('00' + CAST(@idx AS VARCHAR(2)), 2);
    SET @dobStr = CAST((1975 + (@idx % 20)) AS VARCHAR(4)) + '-07-' + RIGHT('0' + CAST((1 + (@idx % 28)) AS VARCHAR(2)), 2);
    SET @cccdStr = '03007' + CAST((1 + (@idx % 8)) AS VARCHAR(1)) + '000' + RIGHT('000' + CAST(@idx AS VARCHAR(3)), 3);
    SET @emailStr = 'driver.tx' + CAST(@idx AS VARCHAR(2)) + '@vexedb.vn';

    INSERT INTO [account] (username, passwordHash, firebaseUid, authProvider, isActive)
    OUTPUT inserted.accountId INTO @IdOutput
    VALUES (@phoneStr, @StaffPwd, NULL, 'local', 1);
    SELECT TOP 1 @GeneratedId = Id FROM @IdOutput; DELETE FROM @IdOutput;

    INSERT INTO [account_role] (accountId, roleId) VALUES (@GeneratedId, 4);
    INSERT INTO [staff] (accountId, ticketAgencyId, staffName, phone, email, dob, cccd, staffPosition, hireDate)
    VALUES (@GeneratedId, NULL, N'Tài Xế Chuyên Nghiệp ' + CAST(@idx AS NVARCHAR(5)), @phoneStr, @emailStr, @dobStr, @cccdStr, 'DRIVER', '2025-01-01');
    SET @idx = @idx + 1;
END;

-- 80 Attendants (local, TRIP_STAFF role, no agency)
SET @idx = 1;
WHILE @idx <= 80
BEGIN
    SET @phoneStr = '0933000' + RIGHT('00' + CAST(@idx AS VARCHAR(2)), 2);
    SET @dobStr = CAST((1985 + (@idx % 20)) AS VARCHAR(4)) + '-09-' + RIGHT('0' + CAST((1 + (@idx % 28)) AS VARCHAR(2)), 2);
    SET @cccdStr = '03008' + CAST((1 + (@idx % 8)) AS VARCHAR(1)) + '000' + RIGHT('000' + CAST(@idx AS VARCHAR(3)), 3);
    SET @emailStr = 'attendant.px' + CAST(@idx AS VARCHAR(2)) + '@vexedb.vn';

    INSERT INTO [account] (username, passwordHash, firebaseUid, authProvider, isActive)
    OUTPUT inserted.accountId INTO @IdOutput
    VALUES (@phoneStr, @StaffPwd, NULL, 'local', 1);
    SELECT TOP 1 @GeneratedId = Id FROM @IdOutput; DELETE FROM @IdOutput;

    INSERT INTO [account_role] (accountId, roleId) VALUES (@GeneratedId, 4);
    INSERT INTO [staff] (accountId, ticketAgencyId, staffName, phone, email, dob, cccd, staffPosition, hireDate)
    VALUES (@GeneratedId, NULL, N'Phụ Xe Tuyến Đường ' + CAST(@idx AS NVARCHAR(5)), @phoneStr, @emailStr, @dobStr, @cccdStr, 'ATTENDANT', '2025-01-01');
    SET @idx = @idx + 1;
END;

-- Registered customers ONLY: every customer row has a Firebase account.
-- Walk-in ticket passengers are NOT inserted into [customer] — their name/phone
-- live on passenger_ticket_detail; passenger_ticket.customerId stays NULL.
-- (SQL Server UNIQUE on customer.accountId also allows only one NULL.)
SET @idx = 1;
WHILE @idx <= 100
BEGIN
    SET @phoneStr = '0960000' + RIGHT('000' + CAST(@idx AS VARCHAR(3)), 3);
    SET @dobStr = CAST((1980 + (@idx % 25)) AS VARCHAR(4)) + '-11-' + RIGHT('0' + CAST((1 + (@idx % 25)) AS VARCHAR(2)), 2);
    -- Fake Firebase UID (~28 chars, unique) — not real Firebase, for DB constraint/demo only
    SET @firebaseUid = 'fbuid' + RIGHT('00000000000000000000000' + CAST(@idx AS VARCHAR(3)), 23);

    INSERT INTO [account] (username, passwordHash, firebaseUid, authProvider, isActive)
    OUTPUT inserted.accountId INTO @IdOutput
    VALUES (@phoneStr, NULL, @firebaseUid, 'firebase', 1);
    SELECT TOP 1 @GeneratedId = Id FROM @IdOutput; DELETE FROM @IdOutput;

    INSERT INTO [account_role] (accountId, roleId) VALUES (@GeneratedId, 5);
    INSERT INTO [customer] (accountId, customerName, phone, email, dob, isActive)
    VALUES (
        @GeneratedId,
        N'Thành Viên App ' + CAST(@idx AS NVARCHAR(5)),
        @phoneStr,
        'user' + CAST(@idx AS VARCHAR(3)) + '@gmail.com',
        @dobStr,
        CASE WHEN @idx = 100 THEN 0 ELSE 1 END -- one inactive registered customer for filter tests
    );
    SET @idx = @idx + 1;
END;

-- Inactive driver (filter test) — agency on corridor, not HCM
INSERT INTO [account] (username, passwordHash, firebaseUid, authProvider, isActive)
OUTPUT inserted.accountId INTO @IdOutput
VALUES ('0999999999', @StaffPwd, NULL, 'local', 1);
SELECT TOP 1 @GeneratedId = Id FROM @IdOutput; DELETE FROM @IdOutput;
INSERT INTO [account_role] (accountId, roleId) VALUES (@GeneratedId, 4);
INSERT INTO [staff] (accountId, ticketAgencyId, staffName, phone, email, dob, cccd, staffPosition, hireDate, isActive)
VALUES (@GeneratedId, NULL, N'Tài Xế Nghỉ Việc', '0999999999', 'taixenghiviec@vexedb.vn', '1992-07-15', '030092000099', 'DRIVER', '2025-06-01', 0);

-- ============================================================================
-- LEVEL 2.5: ROUTE STOPS + PRICES
-- ============================================================================
PRINT N'-> Cấu hình route_stop + bảng giá...';

-- Route 1: Hà Nội -> Quảng Trị (HN stops then QT stops, QL1A north->south)
INSERT INTO [route_stop] (routeId, stopPointId, stopOrder, kilometersFromStart, minutesFromStart) VALUES
(1, 4,  1,   0.00,   0),   -- Nội Bài (pickup đầu)
(1, 3,  2,  35.00,  50),   -- Mỹ Đình
(1, 5,  3,  42.00,  65),   -- Cầu Giấy
(1, 2,  4,  55.00,  85),   -- Giáp Bát
(1, 1,  5,  60.00,  95),   -- Nước Ngầm
(1, 6,  6, 420.00, 480),   -- Ba Đồn
(1, 7,  7, 450.00, 520),   -- Quảng Ninh
(1, 8,  8, 480.00, 560),   -- VP Đồng Hới
(1, 9,  9, 485.00, 570),   -- BX Đồng Hới
(1, 10, 10, 505.00, 600),  -- Phong Nha
(1, 11, 11, 520.00, 620),  -- Lệ Thủy
(1, 12, 12, 530.00, 640);  -- Kiến Giang

-- Route 2: reverse
INSERT INTO [route_stop] (routeId, stopPointId, stopOrder, kilometersFromStart, minutesFromStart) VALUES
(2, 12, 1,    0.00,   0),
(2, 11, 2,   10.00,  20),
(2, 10, 3,   25.00,  40),
(2, 9,  4,   45.00,  70),
(2, 8,  5,   50.00,  80),
(2, 7,  6,   80.00, 120),
(2, 6,  7,  110.00, 160),
(2, 1,  8,  470.00, 545),
(2, 2,  9,  475.00, 555),
(2, 5, 10,  488.00, 575),
(2, 3, 11,  495.00, 590),
(2, 4, 12,  530.00, 640);

INSERT INTO [coach_type_price] (coachTypeId, seatPrice, startEffectiveDate, endEffectiveDate) VALUES
(1, 590000.00, '2026-01-01', '2029-12-31'),
(2, 450000.00, '2026-01-01', '2029-12-31'),
(3, 350000.00, '2026-01-01', '2029-12-31');

-- cargo_type_price.pricePerUnit = SURCHARGE only (FreightCalculatorUtility)
INSERT INTO [cargo_type_price] (cargoTypeId, unit, pricePerUnit, startEffectiveDate, endEffectiveDate) VALUES
(1, N'Thùng',   60000.00,  '2026-01-01', '2029-12-31'),
(2, N'Chiếc',   400000.00, '2026-01-01', '2029-12-31'),
(3, N'Kiện',    150000.00, '2026-01-01', '2029-12-31'),
(4, N'Thùng',   120000.00, '2026-01-01', '2029-12-31'),
(5, N'Món',     200000.00, '2026-01-01', '2029-12-31'),
(6, N'Kiện',    180000.00, '2026-01-01', '2029-12-31'),
(7, N'Kg',      25000.00,  '2026-01-01', '2029-12-31'),
(8, N'Chuyến',  500000.00, '2026-01-01', '2029-12-31');

-- ============================================================================
-- LEVEL 3: COACHES + SEATS
-- ============================================================================
PRINT N'-> Lắp ráp 365 xe + ma trận ghế...';

DECLARE @ManufacturerTable TABLE (Id INT IDENTITY(1,1), Brand NVARCHAR(100));
INSERT INTO @ManufacturerTable (Brand) VALUES
(N'Thaco Mobihome'), (N'Kia Granbird Silk Road'), (N'Hyundai Universe Express'), (N'Volvo B11R Premium'),
(N'Scania Marcopolo'), (N'Tracomeco Universe'), (N'Samco Primas ISUZU'), (N'Daewoo BX212');
DECLARE @TotalBrands INT = (SELECT COUNT(*) FROM @ManufacturerTable);

DECLARE @c INT = 1;
DECLARE @generatedPlate VARCHAR(20);
DECLARE @coachStatus VARCHAR(20);
DECLARE @pickedBrand NVARCHAR(100);
DECLARE @TargetCoachTypeId INT;
DECLARE @NewCoachId INT;
DECLARE @f INT, @r INT, @c_idx INT, @seatCount INT;

WHILE @c <= 365
BEGIN
    SET @generatedPlate = CASE WHEN @c % 3 = 0 THEN '29B-' WHEN @c % 3 = 1 THEN '30B-' ELSE '73B-' END +
                          LEFT(CAST((@c * 137 + 10000) AS VARCHAR(10)), 3) + '.' +
                          RIGHT(CAST((@c * 79 + 10) AS VARCHAR(10)), 2);
    SET @coachStatus = CASE WHEN @c <= 320 THEN 'ACTIVE' ELSE 'MAINTENANCE' END;
    SELECT @pickedBrand = Brand FROM @ManufacturerTable WHERE Id = ((@c % @TotalBrands) + 1);
    SET @TargetCoachTypeId = CASE WHEN @c % 3 = 0 THEN 1 WHEN @c % 3 = 1 THEN 2 ELSE 3 END;

    -- Preferred corridor side; trip seed only assigns matching routeId or NULL coaches
    INSERT INTO [coach] (routeId, coachTypeId, licensePlate, [status], manufacturer, [year])
    VALUES (
        CASE WHEN @c % 5 = 0 THEN NULL WHEN @c % 2 = 0 THEN 1 ELSE 2 END,
        @TargetCoachTypeId, @generatedPlate, @coachStatus, @pickedBrand, 2024
    );

    SET @NewCoachId = SCOPE_IDENTITY();
    SET @seatCount = 1;

    IF @TargetCoachTypeId = 1
    BEGIN
        SET @f = 1;
        WHILE @f <= 2
        BEGIN
            SET @r = 1;
            WHILE @r <= 5
            BEGIN
                SET @c_idx = 1;
                WHILE @c_idx <= 2
                BEGIN
                    INSERT INTO [seat] (coachId, seatCode, rowIndex, colIndex, floorIndex)
                    VALUES (@NewCoachId, 'L' + RIGHT('0' + CAST(@seatCount AS VARCHAR(2)), 2), @r, @c_idx, @f);
                    SET @seatCount = @seatCount + 1;
                    SET @c_idx = @c_idx + 1;
                END;
                SET @r = @r + 1;
            END;
            SET @f = @f + 1;
        END;
    END
    ELSE IF @TargetCoachTypeId = 2
    BEGIN
        SET @f = 1;
        WHILE @f <= 2
        BEGIN
            SET @r = 1;
            WHILE @r <= 6
            BEGIN
                SET @c_idx = 1;
                WHILE @c_idx <= 3
                BEGIN
                    IF (@r <= 5) OR (@r = 6 AND @c_idx = 2)
                    BEGIN
                        INSERT INTO [seat] (coachId, seatCode, rowIndex, colIndex, floorIndex)
                        VALUES (@NewCoachId, 'LX' + RIGHT('0' + CAST(@seatCount AS VARCHAR(2)), 2), @r, @c_idx, @f);
                        SET @seatCount = @seatCount + 1;
                    END;
                    SET @c_idx = @c_idx + 1;
                END;
                SET @r = @r + 1;
            END;
            SET @f = @f + 1;
        END;
    END
    ELSE
    BEGIN
        SET @f = 1;
        WHILE @f <= 2
        BEGIN
            SET @r = 1;
            WHILE @r <= 7
            BEGIN
                SET @c_idx = 1;
                WHILE @c_idx <= 3
                BEGIN
                    IF (@r <= 6) OR (@r = 7 AND @c_idx = 2)
                    BEGIN
                        INSERT INTO [seat] (coachId, seatCode, rowIndex, colIndex, floorIndex)
                        VALUES (@NewCoachId, 'T' + RIGHT('0' + CAST(@seatCount AS VARCHAR(2)), 2), @r, @c_idx, @f);
                        SET @seatCount = @seatCount + 1;
                    END;
                    SET @c_idx = @c_idx + 1;
                END;
                SET @r = @r + 1;
            END;
            SET @f = @f + 1;
        END;
    END

    SET @c = @c + 1;
END;

INSERT INTO [coach_status_log] (coachId, fromStatus, toStatus, reason, expectedEndAt)
SELECT c.coachId, 'ACTIVE', 'MAINTENANCE', N'Bảo dưỡng định kỳ hệ thống phanh', DATEADD(day, 7, GETDATE())
FROM [coach] c
WHERE c.[status] = 'MAINTENANCE'
  AND c.coachId IN (SELECT TOP 5 coachId FROM [coach] WHERE [status] = 'MAINTENANCE' ORDER BY coachId);

-- ============================================================================
-- LEVEL 4: TRIPS + TRIP_SEATS
-- Mirrors dbo.sp_AutoGenerateWeeklySchedule_Final (Procedure.sql):
--   - 24 slots/day route 1 (00:00..23:00)
--   - 24 slots/day route 2 (:30 offset)
--   - @BufferMinutes = 720
--   - last trip must be the opposite route (no teleport)
--   - reserve TOP 150 ACTIVE coaches
-- ============================================================================
PRINT N'-> Sinh chuyến + trip_seat (Procedure-aligned 24x2/ngày, buffer 720)...';

DECLARE @BufferMinutes INT = 720;
DECLARE @StartDate DATE = DATEADD(day, -3, CAST(GETDATE() AS DATE));
DECLARE @EndDate DATE = DATEADD(day, 6, @StartDate); -- 7 days like the weekly procedure
DECLARE @CurrentDate DATE = @StartDate;
DECLARE @TripCounter INT = 0;
DECLARE @SkipCounter INT = 0;
DECLARE @NewTripId INT;
DECLARE @DepartureTime DATETIME;
DECLARE @Slot INT;
DECLARE @PickedCoachId INT;
DECLARE @PickedDriverId INT;
DECLARE @PickedAttendantId INT;

WHILE @CurrentDate <= @EndDate
BEGIN
    ------------------------------------------------------------------
    -- CHIỀU ĐI: route 1 — hourly 0h..23h
    ------------------------------------------------------------------
    SET @Slot = 0;
    WHILE @Slot < 24
    BEGIN
        SET @DepartureTime = DATEADD(HOUR, @Slot, CAST(@CurrentDate AS DATETIME));
        SET @PickedCoachId = NULL;
        SET @PickedDriverId = NULL;
        SET @PickedAttendantId = NULL;

        SELECT TOP 1 @PickedCoachId = c.coachId
        FROM [coach] c
        WHERE c.[status] = 'ACTIVE'
          AND c.coachId NOT IN (
              SELECT TOP 150 coachId FROM [coach] WHERE [status] = 'ACTIVE' ORDER BY coachId ASC
          )
          AND NOT EXISTS (
              SELECT 1 FROM [trip] t
              WHERE t.coachId = c.coachId
                AND t.[status] NOT IN ('CANCELED', 'CANCELLED')
                AND t.departureTime BETWEEN DATEADD(MINUTE, -@BufferMinutes, @DepartureTime)
                                        AND DATEADD(MINUTE, @BufferMinutes, @DepartureTime)
          )
          AND (
              NOT EXISTS (
                  SELECT 1 FROM [trip] lastTrip
                  WHERE lastTrip.coachId = c.coachId
                    AND lastTrip.departureTime < @DepartureTime
                    AND lastTrip.[status] NOT IN ('CANCELED', 'CANCELLED')
              )
              OR (
                  SELECT TOP 1 lastTrip.routeId
                  FROM [trip] lastTrip
                  WHERE lastTrip.coachId = c.coachId
                    AND lastTrip.departureTime < @DepartureTime
                    AND lastTrip.[status] NOT IN ('CANCELED', 'CANCELLED')
                  ORDER BY lastTrip.departureTime DESC
              ) = 2
          )
        ORDER BY NEWID();

        SELECT TOP 1 @PickedDriverId = s.staffId
        FROM [staff] s
        WHERE s.staffPosition = 'DRIVER' AND s.isActive = 1
          AND NOT EXISTS (
              SELECT 1 FROM [trip] t
              WHERE t.driverId = s.staffId
                AND t.[status] NOT IN ('CANCELED', 'CANCELLED')
                AND t.departureTime BETWEEN DATEADD(MINUTE, -@BufferMinutes, @DepartureTime)
                                        AND DATEADD(MINUTE, @BufferMinutes, @DepartureTime)
          )
          AND (
              NOT EXISTS (
                  SELECT 1 FROM [trip] lastTrip
                  WHERE lastTrip.driverId = s.staffId
                    AND lastTrip.departureTime < @DepartureTime
                    AND lastTrip.[status] NOT IN ('CANCELED', 'CANCELLED')
              )
              OR (
                  SELECT TOP 1 lastTrip.routeId
                  FROM [trip] lastTrip
                  WHERE lastTrip.driverId = s.staffId
                    AND lastTrip.departureTime < @DepartureTime
                    AND lastTrip.[status] NOT IN ('CANCELED', 'CANCELLED')
                  ORDER BY lastTrip.departureTime DESC
              ) = 2
          )
        ORDER BY NEWID();

        SELECT TOP 1 @PickedAttendantId = s.staffId
        FROM [staff] s
        WHERE s.staffPosition = 'ATTENDANT' AND s.isActive = 1
          AND s.staffId <> ISNULL(@PickedDriverId, -1)
          AND NOT EXISTS (
              SELECT 1 FROM [trip] t
              WHERE t.attendantId = s.staffId
                AND t.[status] NOT IN ('CANCELED', 'CANCELLED')
                AND t.departureTime BETWEEN DATEADD(MINUTE, -@BufferMinutes, @DepartureTime)
                                        AND DATEADD(MINUTE, @BufferMinutes, @DepartureTime)
          )
          AND (
              NOT EXISTS (
                  SELECT 1 FROM [trip] lastTrip
                  WHERE lastTrip.attendantId = s.staffId
                    AND lastTrip.departureTime < @DepartureTime
                    AND lastTrip.[status] NOT IN ('CANCELED', 'CANCELLED')
              )
              OR (
                  SELECT TOP 1 lastTrip.routeId
                  FROM [trip] lastTrip
                  WHERE lastTrip.attendantId = s.staffId
                    AND lastTrip.departureTime < @DepartureTime
                    AND lastTrip.[status] NOT IN ('CANCELED', 'CANCELLED')
                  ORDER BY lastTrip.departureTime DESC
              ) = 2
          )
        ORDER BY NEWID();

        IF @PickedCoachId IS NOT NULL AND @PickedDriverId IS NOT NULL AND @PickedAttendantId IS NOT NULL
        BEGIN
            INSERT INTO [trip] (routeId, coachId, departureTime, [status], driverId, attendantId)
            VALUES (1, @PickedCoachId, @DepartureTime, 'SCHEDULED', @PickedDriverId, @PickedAttendantId);

            SET @NewTripId = SCOPE_IDENTITY();

            INSERT INTO [trip_seat] (tripId, seatId, price, [status])
            SELECT @NewTripId, s.seatId, ctp.seatPrice, 'AVAILABLE'
            FROM [seat] s
            JOIN [coach] c ON s.coachId = c.coachId
            JOIN [coach_type_price] ctp ON c.coachTypeId = ctp.coachTypeId
            WHERE c.coachId = @PickedCoachId
              AND @DepartureTime BETWEEN ctp.startEffectiveDate AND ctp.endEffectiveDate
              AND s.isActive = 1;

            SET @TripCounter = @TripCounter + 1;
        END
        ELSE
            SET @SkipCounter = @SkipCounter + 1;

        SET @Slot = @Slot + 1;
    END;

    ------------------------------------------------------------------
    -- CHIỀU VỀ: route 2 — hourly :30
    ------------------------------------------------------------------
    SET @Slot = 0;
    WHILE @Slot < 24
    BEGIN
        SET @DepartureTime = DATEADD(MINUTE, (@Slot * 60) + 30, CAST(@CurrentDate AS DATETIME));
        SET @PickedCoachId = NULL;
        SET @PickedDriverId = NULL;
        SET @PickedAttendantId = NULL;

        SELECT TOP 1 @PickedCoachId = c.coachId
        FROM [coach] c
        WHERE c.[status] = 'ACTIVE'
          AND c.coachId NOT IN (
              SELECT TOP 150 coachId FROM [coach] WHERE [status] = 'ACTIVE' ORDER BY coachId ASC
          )
          AND NOT EXISTS (
              SELECT 1 FROM [trip] t
              WHERE t.coachId = c.coachId
                AND t.[status] NOT IN ('CANCELED', 'CANCELLED')
                AND t.departureTime BETWEEN DATEADD(MINUTE, -@BufferMinutes, @DepartureTime)
                                        AND DATEADD(MINUTE, @BufferMinutes, @DepartureTime)
          )
          AND (
              NOT EXISTS (
                  SELECT 1 FROM [trip] lastTrip
                  WHERE lastTrip.coachId = c.coachId
                    AND lastTrip.departureTime < @DepartureTime
                    AND lastTrip.[status] NOT IN ('CANCELED', 'CANCELLED')
              )
              OR (
                  SELECT TOP 1 lastTrip.routeId
                  FROM [trip] lastTrip
                  WHERE lastTrip.coachId = c.coachId
                    AND lastTrip.departureTime < @DepartureTime
                    AND lastTrip.[status] NOT IN ('CANCELED', 'CANCELLED')
                  ORDER BY lastTrip.departureTime DESC
              ) = 1
          )
        ORDER BY NEWID();

        SELECT TOP 1 @PickedDriverId = s.staffId
        FROM [staff] s
        WHERE s.staffPosition = 'DRIVER' AND s.isActive = 1
          AND NOT EXISTS (
              SELECT 1 FROM [trip] t
              WHERE t.driverId = s.staffId
                AND t.[status] NOT IN ('CANCELED', 'CANCELLED')
                AND t.departureTime BETWEEN DATEADD(MINUTE, -@BufferMinutes, @DepartureTime)
                                        AND DATEADD(MINUTE, @BufferMinutes, @DepartureTime)
          )
          AND (
              NOT EXISTS (
                  SELECT 1 FROM [trip] lastTrip
                  WHERE lastTrip.driverId = s.staffId
                    AND lastTrip.departureTime < @DepartureTime
                    AND lastTrip.[status] NOT IN ('CANCELED', 'CANCELLED')
              )
              OR (
                  SELECT TOP 1 lastTrip.routeId
                  FROM [trip] lastTrip
                  WHERE lastTrip.driverId = s.staffId
                    AND lastTrip.departureTime < @DepartureTime
                    AND lastTrip.[status] NOT IN ('CANCELED', 'CANCELLED')
                  ORDER BY lastTrip.departureTime DESC
              ) = 1
          )
        ORDER BY NEWID();

        SELECT TOP 1 @PickedAttendantId = s.staffId
        FROM [staff] s
        WHERE s.staffPosition = 'ATTENDANT' AND s.isActive = 1
          AND s.staffId <> ISNULL(@PickedDriverId, -1)
          AND NOT EXISTS (
              SELECT 1 FROM [trip] t
              WHERE t.attendantId = s.staffId
                AND t.[status] NOT IN ('CANCELED', 'CANCELLED')
                AND t.departureTime BETWEEN DATEADD(MINUTE, -@BufferMinutes, @DepartureTime)
                                        AND DATEADD(MINUTE, @BufferMinutes, @DepartureTime)
          )
          AND (
              NOT EXISTS (
                  SELECT 1 FROM [trip] lastTrip
                  WHERE lastTrip.attendantId = s.staffId
                    AND lastTrip.departureTime < @DepartureTime
                    AND lastTrip.[status] NOT IN ('CANCELED', 'CANCELLED')
              )
              OR (
                  SELECT TOP 1 lastTrip.routeId
                  FROM [trip] lastTrip
                  WHERE lastTrip.attendantId = s.staffId
                    AND lastTrip.departureTime < @DepartureTime
                    AND lastTrip.[status] NOT IN ('CANCELED', 'CANCELLED')
                  ORDER BY lastTrip.departureTime DESC
              ) = 1
          )
        ORDER BY NEWID();

        IF @PickedCoachId IS NOT NULL AND @PickedDriverId IS NOT NULL AND @PickedAttendantId IS NOT NULL
        BEGIN
            INSERT INTO [trip] (routeId, coachId, departureTime, [status], driverId, attendantId)
            VALUES (2, @PickedCoachId, @DepartureTime, 'SCHEDULED', @PickedDriverId, @PickedAttendantId);

            SET @NewTripId = SCOPE_IDENTITY();

            INSERT INTO [trip_seat] (tripId, seatId, price, [status])
            SELECT @NewTripId, s.seatId, ctp.seatPrice, 'AVAILABLE'
            FROM [seat] s
            JOIN [coach] c ON s.coachId = c.coachId
            JOIN [coach_type_price] ctp ON c.coachTypeId = ctp.coachTypeId
            WHERE c.coachId = @PickedCoachId
              AND @DepartureTime BETWEEN ctp.startEffectiveDate AND ctp.endEffectiveDate
              AND s.isActive = 1;

            SET @TripCounter = @TripCounter + 1;
        END
        ELSE
            SET @SkipCounter = @SkipCounter + 1;

        SET @Slot = @Slot + 1;
    END;

    SET @CurrentDate = DATEADD(day, 1, @CurrentDate);
END;

PRINT N' -> Tổng số chuyến: ' + CAST(@TripCounter AS VARCHAR(10))
    + N' | slot thiếu resource: ' + CAST(@SkipCounter AS VARCHAR(10));

-- ============================================================================
-- LEVEL 5.1: PASSENGER TICKETS (itinerary respects route_stop order)
-- ============================================================================
PRINT N'-> Sinh vé hành khách mẫu...';

DECLARE @TicketIdx INT = 1;
DECLARE @MinNewTripId INT = (SELECT MIN(tripId) FROM [trip]);
DECLARE @MaxNewTripId INT = (SELECT MAX(tripId) FROM [trip]);
DECLARE @MinCusId INT = (SELECT MIN(customerId) FROM [customer]);
DECLARE @MaxCusId INT = (SELECT MAX(customerId) FROM [customer]);
DECLARE @TicketStaffId INT = (SELECT MIN(staffId) FROM [staff] WHERE staffPosition = 'TICKET_STAFF');

DECLARE @TargetTripId INT;
DECLARE @TargetCoachTypeId2 INT;
DECLARE @CalculatedTicketPrice DECIMAL(15,2);
DECLARE @TargetTripSeatId INT;
DECLARE @TargetCusId INT;
DECLARE @TicketStatus VARCHAR(20);
DECLARE @SeatStatusUpdate VARCHAR(20);
DECLARE @SeatCodeSnapshot VARCHAR(10);
DECLARE @NewPId INT;
DECLARE @TripRouteId INT;
DECLARE @TripDeparture DATETIME;
DECLARE @PickupStopId INT;
DECLARE @DropoffStopId INT;
DECLARE @PickupStopNameSnap NVARCHAR(255);
DECLARE @DropoffStopNameSnap NVARCHAR(255);
DECLARE @PickupSurcharge DECIMAL(15,2);
DECLARE @DropoffSurcharge DECIMAL(15,2);
DECLARE @BaseSeatPrice DECIMAL(15,2);
DECLARE @MaxStopOrder INT;
DECLARE @PickupOrder INT;
DECLARE @DropoffOrder INT;
DECLARE @PassengerCreated INT = 0;

WHILE @TicketIdx <= 220 AND @MinNewTripId IS NOT NULL
BEGIN
    SET @TargetTripId = @MinNewTripId + (@TicketIdx % (@MaxNewTripId - @MinNewTripId + 1));

    SELECT @TripRouteId = t.routeId, @TargetCoachTypeId2 = c.coachTypeId, @TripDeparture = t.departureTime
    FROM [trip] t
    JOIN [coach] c ON t.coachId = c.coachId
    WHERE t.tripId = @TargetTripId;

    SELECT @BaseSeatPrice = seatPrice FROM [coach_type_price] WHERE coachTypeId = @TargetCoachTypeId2;

    SELECT @MaxStopOrder = MAX(stopOrder) FROM [route_stop] WHERE routeId = @TripRouteId;

    SET @PickupOrder = 1 + (@TicketIdx % CASE WHEN @MaxStopOrder > 3 THEN 3 ELSE 1 END);
    SET @DropoffOrder = @MaxStopOrder - (@TicketIdx % CASE WHEN @MaxStopOrder > 3 THEN 3 ELSE 0 END);
    IF @DropoffOrder <= @PickupOrder SET @DropoffOrder = @MaxStopOrder;

    SELECT @PickupStopId = rs.stopPointId, @PickupStopNameSnap = cs.stopPointName, @PickupSurcharge = cs.surcharge
    FROM [route_stop] rs
    JOIN [coach_stop] cs ON rs.stopPointId = cs.stopPointId
    WHERE rs.routeId = @TripRouteId AND rs.stopOrder = @PickupOrder;

    SELECT @DropoffStopId = rs.stopPointId, @DropoffStopNameSnap = cs.stopPointName, @DropoffSurcharge = cs.surcharge
    FROM [route_stop] rs
    JOIN [coach_stop] cs ON rs.stopPointId = cs.stopPointId
    WHERE rs.routeId = @TripRouteId AND rs.stopOrder = @DropoffOrder;

    SET @CalculatedTicketPrice = @BaseSeatPrice + ISNULL(@PickupSurcharge, 0) + ISNULL(@DropoffSurcharge, 0);

    SET @TargetCusId = CASE
        WHEN @TicketIdx % 4 = 0 THEN NULL
        ELSE @MinCusId + (@TicketIdx % (@MaxCusId - @MinCusId + 1))
    END;
    SET @TicketStatus = CASE WHEN @TicketIdx % 7 = 0 THEN 'PENDING' ELSE 'CONFIRMED' END;
    SET @SeatStatusUpdate = CASE WHEN @TicketStatus = 'PENDING' THEN 'LOCKED' ELSE 'SOLD' END;

    SET @TargetTripSeatId = NULL;
    SELECT TOP 1 @TargetTripSeatId = tripSeatId
    FROM [trip_seat]
    WHERE tripId = @TargetTripId AND [status] = 'AVAILABLE'
    ORDER BY seatId;

    IF @TargetTripSeatId IS NOT NULL AND @PickupStopId IS NOT NULL AND @DropoffStopId IS NOT NULL
    BEGIN
        UPDATE [trip_seat] SET [status] = @SeatStatusUpdate WHERE tripSeatId = @TargetTripSeatId;

        SELECT @SeatCodeSnapshot = s.seatCode
        FROM [trip_seat] ts
        JOIN [seat] s ON ts.seatId = s.seatId
        WHERE ts.tripSeatId = @TargetTripSeatId;

        INSERT INTO [passenger_ticket] (
            customerId, tripId, voucherId, soldBy, ticketCode, totalPrice,
            pickupStopId, dropoffStopId, pickupStopName, dropoffStopName,
            voucherCodeSnapshot, refundPolicyDepartureTime, [status]
        )
        VALUES (
            @TargetCusId, @TargetTripId, NULL,
            CASE WHEN @TargetCusId IS NULL OR @TicketIdx % 3 = 0 THEN @TicketStaffId ELSE NULL END,
            'TK_SYS_' + RIGHT('0000' + CAST(@TicketIdx AS VARCHAR(4)), 4),
            @CalculatedTicketPrice, @PickupStopId, @DropoffStopId,
            @PickupStopNameSnap, @DropoffStopNameSnap, NULL, @TripDeparture, @TicketStatus
        );

        SET @NewPId = SCOPE_IDENTITY();

        INSERT INTO [passenger_ticket_detail] (passengerTicketId, tripSeatId, seatCodeSnapshot, qrcode, fullName, phone, price, [status])
        VALUES (
            @NewPId, @TargetTripSeatId, @SeatCodeSnapshot,
            'TOKEN_QR_SECURE_' + CAST(NEWID() AS VARCHAR(36)),
            N'Hành Khách Ghế ' + CAST(@TicketIdx AS NVARCHAR(5)),
            '0985' + RIGHT('00000' + CAST(@TicketIdx AS VARCHAR(5)), 5),
            @CalculatedTicketPrice, @TicketStatus
        );

        INSERT INTO [payment] (passengerTicketId, cargoTicketId, amount, paymentMethod, transactionId, [status], paymentTime)
        VALUES (
            @NewPId, NULL, @CalculatedTicketPrice, 'BANK_TRANSFER',
            'TXN_P_' + CAST(@TicketIdx AS VARCHAR(5)),
            CASE WHEN @TicketStatus = 'CONFIRMED' THEN 'COMPLETED' ELSE 'PENDING' END,
            CASE WHEN @TicketStatus = 'CONFIRMED' THEN GETDATE() ELSE NULL END
        );

        SET @PassengerCreated = @PassengerCreated + 1;
    END

    SET @TicketIdx = @TicketIdx + 1;
END;

PRINT N' -> Vé HK tạo được: ' + CAST(@PassengerCreated AS VARCHAR(10));

-- ============================================================================
-- LEVEL 5.2: LIFECYCLE SAMPLES (in-progress, incident, check-in, refund)
-- ============================================================================
PRINT N'-> Gắn sample lifecycle vận hành...';

DECLARE @LifeTripId INT;
DECLARE @LifeCoachId INT;
DECLARE @LifeDriverId INT;
DECLARE @PastTripId INT;
DECLARE @CancelTicketId INT;
DECLARE @CancelPaymentId INT;
DECLARE @CancelDetailId INT;
DECLARE @CheckedTripId INT;
DECLARE @IncidentTripId INT;
DECLARE @IncidentCoachId INT;

SELECT TOP 1 @LifeTripId = t.tripId, @LifeCoachId = t.coachId, @LifeDriverId = t.driverId
FROM [trip] t
WHERE t.[status] = 'SCHEDULED'
  AND t.departureTime <= DATEADD(hour, 2, GETDATE())
  AND t.departureTime >= DATEADD(hour, -6, GETDATE())
ORDER BY ABS(DATEDIFF(minute, t.departureTime, GETDATE()));

IF @LifeTripId IS NULL
BEGIN
    SELECT TOP 1 @LifeTripId = t.tripId, @LifeCoachId = t.coachId, @LifeDriverId = t.driverId
    FROM [trip] t
    WHERE t.[status] = 'SCHEDULED'
    ORDER BY ABS(DATEDIFF(minute, t.departureTime, GETDATE()));
END

IF @LifeTripId IS NOT NULL
BEGIN
    UPDATE [trip] SET [status] = 'IN_PROGRESS'
    WHERE tripId = @LifeTripId;

    UPDATE ptd
    SET [status] = 'CHECKED_IN'
    FROM [passenger_ticket_detail] ptd
    JOIN [passenger_ticket] pt ON pt.passengerTicketId = ptd.passengerTicketId
    WHERE pt.tripId = @LifeTripId AND pt.[status] = 'CONFIRMED' AND ptd.[status] = 'CONFIRMED';

    SET @CheckedTripId = @LifeTripId;
END

SELECT TOP 1 @PastTripId = t.tripId
FROM [trip] t
WHERE t.[status] = 'SCHEDULED'
  AND t.departureTime < DATEADD(day, -1, GETDATE())
  AND (@LifeTripId IS NULL OR t.tripId <> @LifeTripId)
ORDER BY t.departureTime DESC;

IF @PastTripId IS NOT NULL
BEGIN
    UPDATE [trip] SET [status] = 'COMPLETED' WHERE tripId = @PastTripId;
END

SELECT TOP 1 @IncidentTripId = t.tripId, @IncidentCoachId = t.coachId
FROM [trip] t
WHERE t.[status] = 'SCHEDULED'
  AND t.departureTime BETWEEN DATEADD(hour, -3, GETDATE()) AND DATEADD(hour, 6, GETDATE())
  AND (@LifeTripId IS NULL OR t.tripId <> @LifeTripId)
ORDER BY t.departureTime;

IF @IncidentTripId IS NOT NULL
BEGIN
    UPDATE [trip]
    SET [status] = 'IN_PROGRESS'
    WHERE tripId = @IncidentTripId;

    UPDATE [coach] SET [status] = 'HAVE_INCIDENT' WHERE coachId = @IncidentCoachId;

    INSERT INTO [coach_status_log] (coachId, fromStatus, toStatus, reason, createdAt, createdBy)
    VALUES (
        @IncidentCoachId, 'ACTIVE', 'HAVE_INCIDENT',
        N'Xe gặp sự cố không thể khắc phục trong chuyến #' + CAST(@IncidentTripId AS NVARCHAR(20)),
        GETDATE(), @LifeDriverId
    );
END

SELECT TOP 1
    @CancelTicketId = pt.passengerTicketId,
    @CancelPaymentId = p.paymentId,
    @CancelDetailId = ptd.ticketDetailId
FROM [passenger_ticket] pt
JOIN [passenger_ticket_detail] ptd ON ptd.passengerTicketId = pt.passengerTicketId
JOIN [payment] p ON p.passengerTicketId = pt.passengerTicketId
WHERE pt.[status] = 'CONFIRMED'
  AND p.[status] = 'COMPLETED'
  AND ptd.[status] = 'CONFIRMED'
  AND (@CheckedTripId IS NULL OR pt.tripId <> @CheckedTripId)
  AND (@IncidentTripId IS NULL OR pt.tripId <> @IncidentTripId)
ORDER BY pt.passengerTicketId;

IF @CancelTicketId IS NOT NULL
BEGIN
    UPDATE [passenger_ticket] SET [status] = 'CANCELLED', majorChangeType = 'CANCEL_PARTIAL' WHERE passengerTicketId = @CancelTicketId;
    UPDATE [passenger_ticket_detail] SET [status] = 'CANCELLED' WHERE passengerTicketId = @CancelTicketId;
    UPDATE ts SET [status] = 'AVAILABLE'
    FROM [trip_seat] ts
    JOIN [passenger_ticket_detail] ptd ON ptd.tripSeatId = ts.tripSeatId
    WHERE ptd.passengerTicketId = @CancelTicketId;

    UPDATE [payment]
    SET refundAmount = amount * 0.70
    WHERE paymentId = @CancelPaymentId;

    INSERT INTO [refund] (paymentId, amount, reason, refundMethod, transactionId, [status], refundTime)
    VALUES (
        @CancelPaymentId,
        (SELECT amount * 0.70 FROM [payment] WHERE paymentId = @CancelPaymentId),
        N'Khách hủy vé trước giờ khởi hành (seed)',
        'BANK_TRANSFER',
        'RFND_' + CAST(@CancelTicketId AS VARCHAR(20)),
        'COMPLETED',
        GETDATE()
    );
END

INSERT INTO [accompanied_child] (ticketDetailId, [fullname], birthYear)
SELECT TOP 1 ptd.ticketDetailId, N'Em Bé Đi Kèm', YEAR(GETDATE()) - 5
FROM [passenger_ticket_detail] ptd
JOIN [passenger_ticket] pt ON pt.passengerTicketId = ptd.passengerTicketId
WHERE pt.[status] = 'CONFIRMED' AND ptd.[status] IN ('CONFIRMED', 'CHECKED_IN')
  AND NOT EXISTS (SELECT 1 FROM [accompanied_child] ac WHERE ac.ticketDetailId = ptd.ticketDetailId);

PRINT N'=== SEED fakedata1 HOÀN TẤT ===';
GO

-- Quick sanity checks (optional)
SELECT city, COUNT(*) AS stopCount FROM [coach_stop] GROUP BY city;
SELECT r.routeName, COUNT(rs.routeStopId) AS stopCount
FROM [route] r JOIN [route_stop] rs ON r.routeId = rs.routeId
GROUP BY r.routeName;
SELECT authProvider, COUNT(*) AS cnt FROM [account] GROUP BY authProvider;
SELECT ct.cargoTypeName, ctp.unit, ctp.pricePerUnit
FROM [cargo_type] ct
JOIN [cargo_type_price] ctp ON ct.cargoTypeId = ctp.cargoTypeId;
SELECT COUNT(*) AS agencies, (SELECT COUNT(*) FROM coach_stop) AS stops FROM [ticket_agency];
SELECT [status], COUNT(*) AS trips FROM [trip] GROUP BY [status];
SELECT [status], COUNT(*) AS coaches FROM [coach] GROUP BY [status];
SELECT COUNT(*) AS cargoTickets FROM [cargo_ticket];
SELECT COUNT(*) AS refunds FROM [refund];
SELECT COUNT(*) AS kids FROM [accompanied_child];

-- RECHECK QUERY AFTER SEED
SELECT * FROM [account];
SELECT * FROM [role];
SELECT * FROM [voucher];
SELECT * FROM [coach_stop];
SELECT * FROM [route];
SELECT * FROM [coach_type];
SELECT * FROM [cargo_type];
SELECT * FROM [account_role];
SELECT * FROM [customer];
SELECT * FROM [ticket_agency];
SELECT * FROM [staff];
SELECT * FROM [route_stop];
SELECT * FROM [seat];
SELECT * FROM [coach_type_price];
SELECT * FROM [cargo_type_price];
SELECT * FROM [coach];
SELECT * FROM [trip];
SELECT * FROM [trip_seat];
SELECT * FROM [passenger_ticket];
SELECT * FROM [cargo_ticket];
SELECT * FROM [passenger_ticket_detail];
SELECT * FROM [cargo_ticket_detail];
SELECT * FROM [payment];
SELECT * FROM [accompanied_child];
SELECT * FROM [refund];
