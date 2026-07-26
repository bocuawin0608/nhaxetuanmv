USE master;
GO

-- Xóa theo thứ tự từ LEVEL cao xuống LEVEL thấp để không dính Foreign Key Constraints
DROP TABLE IF EXISTS [refund];
DROP TABLE IF EXISTS [accompanied_child];
DROP TABLE IF EXISTS [payment];
DROP TABLE IF EXISTS [cargo_ticket_detail];
DROP TABLE IF EXISTS [passenger_ticket_detail];
DROP TABLE IF EXISTS [cargo_ticket];
DROP TABLE IF EXISTS [passenger_ticket];
DROP TABLE IF EXISTS [trip_seat];
DROP TABLE IF EXISTS [trip];
DROP TABLE IF EXISTS [seat];
DROP TABLE IF EXISTS [coach_status_log];
DROP TABLE IF EXISTS [coach];
DROP TABLE IF EXISTS [cargo_type_price];
DROP TABLE IF EXISTS [coach_type_price];
DROP TABLE IF EXISTS [route_stop];
DROP TABLE IF EXISTS [staff];
DROP TABLE IF EXISTS [ticket_agency];
DROP TABLE IF EXISTS [customer];
DROP TABLE IF EXISTS [account_role];
DROP TABLE IF EXISTS [cargo_type];
DROP TABLE IF EXISTS [coach_type];
DROP TABLE IF EXISTS [route];
DROP TABLE IF EXISTS [coach_stop];
DROP TABLE IF EXISTS [voucher];
DROP TABLE IF EXISTS [role];
DROP TABLE IF EXISTS [account];
GO
USE master;
GO
IF EXISTS (SELECT * FROM sys.databases WHERE name = 'VeXeDB')
BEGIN
    ALTER DATABASE VeXeDB SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE VeXeDB;
    PRINT 'Đã xóa Database cũ thành công!';
END
GO

CREATE DATABASE VeXeDB;
GO
USE VeXeDB;
GO

-- ============================================================================
-- LEVEL 1: STRONG ENTITIES (Các bảng danh mục và thực thể độc lập)
-- ============================================================================

CREATE TABLE [account] (
    [accountId] INT IDENTITY(1,1) PRIMARY KEY,
    [username] VARCHAR(50) NOT NULL UNIQUE,
    -- Staff: số điện thoại. Customer qua form: số điện thoại. 
    -- Customer qua Google: email Google hoặc uid Firebase.
    [passwordHash] VARCHAR(255) NULL,
    -- Staff: bcrypt hash của mật khẩu do Admin cấp.
    -- Customer Firebase: NULL — Firebase giữ password, BE không cần lưu.
    [firebaseUid] VARCHAR(128) NULL,
    -- Customer Firebase/Google/Facebook: uid từ Firebase.
    -- Staff: NULL — không dùng Firebase.
    [authProvider]  VARCHAR(20) NOT NULL DEFAULT 'local',
    -- 'local'    → Staff, login bằng username + password
    -- 'firebase' → Customer đăng ký bằng form (Firebase xác thực)
    -- 'google'   → Customer đăng nhập Google
    -- 'facebook' → Customer đăng nhập Facebook
    [isActive] BIT NOT NULL DEFAULT 1,
    [lastLogin] DATETIME NULL,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,

    CONSTRAINT CK_Account_Provider CHECK (
        [authProvider] IN ('local', 'firebase', 'google', 'facebook')
    ),
    CONSTRAINT CK_Account_Credentials CHECK (
        -- Staff (local) phải có password, không cần firebaseUid
        ([authProvider] = 'local'    AND [passwordHash] IS NOT NULL) OR
        -- Customer (Firebase) phải có firebaseUid, không cần password
        ([authProvider] != 'local'   AND [firebaseUid]  IS NOT NULL)
    )
);

-- THIẾT KẾ FIREBASEUID MUST BE UNIQUE NẾU KO NULL: TẠO FILTERED UNIQUE INDEX 
-- Index này đảm bảo: Nếu có giá trị thì phải Unique, nhưng cho phép vô số giá trị NULL
CREATE UNIQUE NONCLUSTERED INDEX UQ_Account_FirebaseUid
ON [account]([firebaseUid])
WHERE [firebaseUid] IS NOT NULL;
GO

CREATE TABLE [role] (
    [roleId] INT IDENTITY(1,1) PRIMARY KEY,
    [roleName] NVARCHAR(50) NOT NULL UNIQUE,
    [isActive] BIT NOT NULL DEFAULT 1,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL
);

CREATE TABLE [voucher] (
    [voucherId] INT IDENTITY(1,1) PRIMARY KEY,
    [voucherCode] VARCHAR(50) NOT NULL UNIQUE,
    [discountValue] DECIMAL(15, 2) NOT NULL,
    [startEffectiveDate] DATETIME NOT NULL,
    [endEffectiveDate] DATETIME NOT NULL,
    [discountType] VARCHAR(20) NOT NULL, 
    [maxDiscountValue] DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    [minOrderValue] DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    [usageLimit] INT NOT NULL DEFAULT 0,
    [usedCount] INT NOT NULL DEFAULT 0,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,

	CONSTRAINT CK_Voucher_Value CHECK ([discountValue] > 0),
    CONSTRAINT CK_Voucher_MaxDiscount CHECK ([maxDiscountValue] >= 0),
    CONSTRAINT CK_Voucher_MinOrder CHECK ([minOrderValue] >= 0),
    CONSTRAINT CK_Voucher_Limits CHECK ([usageLimit] >= 0 AND [usedCount] >= 0),
    CONSTRAINT CK_Voucher_Dates CHECK ([endEffectiveDate] >= [startEffectiveDate]),
    CONSTRAINT CK_Voucher_Type CHECK ([discountType] IN ('PERCENT', 'FIXED'))
);

CREATE TABLE [coach_stop] (
    [stopPointId] INT IDENTITY(1,1) PRIMARY KEY,
    [stopPointName] NVARCHAR(255) NOT NULL,
    [address] NVARCHAR(MAX) NOT NULL,
    [city] NVARCHAR(255) NOT NULL,
    [surcharge] DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    [isActive] BIT NOT NULL DEFAULT 1,
    [latitude] DECIMAL(20, 16) NOT NULL,
    [longitude] DECIMAL(20, 16) NOT NULL,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL
);

CREATE TABLE [route] (
    [routeId] INT IDENTITY(1,1) PRIMARY KEY,
    [routeName] NVARCHAR(255) NOT NULL, 
    [totalKilometers] DECIMAL(8, 2) NOT NULL,
    [totalMinutes] INT NOT NULL,
    [isActive] BIT NOT NULL DEFAULT 1,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,

	CONSTRAINT CK_Route_Distance CHECK ([totalKilometers] > 0),
    CONSTRAINT CK_Route_Time CHECK ([totalMinutes] > 0)
);

CREATE TABLE [coach_type] (
    [coachTypeId] INT IDENTITY(1,1) PRIMARY KEY,
    [coachTypeName] NVARCHAR(100) NOT NULL,
    [totalSeat] INT NOT NULL,
    [isActive] BIT NOT NULL DEFAULT 1,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,

	CONSTRAINT CK_SeatLayout_Total CHECK ([totalSeat] > 0)
);

ALTER TABLE [coach_type] ADD CONSTRAINT UQ_CoachType_Name UNIQUE ([coachTypeName]);
ALTER TABLE [coach_type] ADD [seatLayout] NVARCHAR(MAX) NULL;

CREATE TABLE [cargo_type] (
    [cargoTypeId] INT IDENTITY(1,1) PRIMARY KEY,
    [cargoTypeName] NVARCHAR(100) NOT NULL,
    [isActive] BIT NOT NULL DEFAULT 1,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL
);

-- ============================================================================
-- LEVEL 2: ASSOCIATIVE & WEAK ENTITIES (Cấp phụ thuộc 1)
-- ============================================================================

CREATE TABLE [account_role] (
	accountRoleId INT IDENTITY(1,1) PRIMARY KEY,
    accountId INT NOT NULL,
    roleId INT NOT NULL,    
    FOREIGN KEY (accountId) REFERENCES [account](accountId),
    FOREIGN KEY (roleId) REFERENCES [role](roleId),

	CONSTRAINT UQ_Account_Role UNIQUE (accountId, roleId),
);

CREATE TABLE [customer] (
    [customerId] INT IDENTITY(1,1) PRIMARY KEY,
    [accountId] INT NULL UNIQUE, -- Nullable cho khách vãng lai
    [customerName] NVARCHAR(100) NOT NULL,
    [phone] VARCHAR(20) NULL,
    [email] VARCHAR(100) NULL,
    [dob] DATE NULL,
    [isActive] BIT NOT NULL DEFAULT 1,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,
    FOREIGN KEY ([accountId]) REFERENCES [account] ([accountId]) ON DELETE SET NULL
);

CREATE TABLE [ticket_agency] (
    [ticketAgencyId] INT IDENTITY(1,1) PRIMARY KEY,
    [stopPointId] INT NOT NULL,
    [ticketAgencyName] NVARCHAR(255) NOT NULL,
    [isActive] BIT NOT NULL DEFAULT 1,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,
    FOREIGN KEY ([stopPointId]) REFERENCES [coach_stop] ([stopPointId])
);

CREATE TABLE [staff] (
    [staffId] INT IDENTITY(1,1) PRIMARY KEY,
    [accountId] INT NULL UNIQUE, -- Nullable khi mới onboard
    [ticketAgencyId] INT NULL, -- Nullable nếu là tài xế/phụ xe
    [staffName] NVARCHAR(100) NOT NULL,
    [phone] VARCHAR(20) NOT NULL,
    [email] VARCHAR(100) NULL,
    [dob] DATE NULL,
    [cccd] VARCHAR(20) NULL,
    [staffPosition] VARCHAR(50) NOT NULL, 
    [hireDate] DATE NOT NULL,
    [isActive] BIT NOT NULL DEFAULT 1,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,
    FOREIGN KEY ([accountId]) REFERENCES [account] ([accountId]),
    FOREIGN KEY ([ticketAgencyId]) REFERENCES [ticket_agency] ([ticketAgencyId]) ON DELETE SET NULL,

	CONSTRAINT CK_Staff_Position CHECK ([staffPosition] IN ('DRIVER', 'ATTENDANT', 'TICKET_STAFF', 'MANAGER'))
);

CREATE TABLE [route_stop] (
    [routeStopId] INT IDENTITY(1,1) PRIMARY KEY,
    [routeId] INT NOT NULL,
    [stopPointId] INT NOT NULL,
    [stopOrder] INT NOT NULL, -- Thứ tự điểm dừng
    [kilometersFromStart] DECIMAL(8, 2) NOT NULL,
    [minutesFromStart] INT NOT NULL,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,
    FOREIGN KEY ([routeId]) REFERENCES [route] ([routeId]),
    FOREIGN KEY ([stopPointId]) REFERENCES [coach_stop] ([stopPointId]),

	CONSTRAINT CK_RouteStop_Order CHECK ([stopOrder] >= 1),
    CONSTRAINT CK_RouteStop_Metrics CHECK (([kilometersFromStart] > 0 AND [minutesFromStart] > 0) OR ([kilometersFromStart] = 0 AND [minutesFromStart] = 0))
);

CREATE TABLE [coach_type_price] (
    [coachTypePriceId] INT IDENTITY(1,1) PRIMARY KEY,
    [coachTypeId] INT NOT NULL,
    [seatPrice] DECIMAL(15, 2) NOT NULL,
    [startEffectiveDate] DATETIME NOT NULL,
    [endEffectiveDate] DATETIME NOT NULL,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,
    FOREIGN KEY ([coachTypeId]) REFERENCES [coach_type] ([coachTypeId]),

	CONSTRAINT CK_SeatPrice_Value CHECK ([seatPrice] >= 0),
    CONSTRAINT CK_SeatPrice_Dates CHECK ([endEffectiveDate] >= [startEffectiveDate])
);

ALTER TABLE [coach_type_price] ADD CONSTRAINT UQ_SeatPrice_Timeline UNIQUE ([coachTypeId], [startEffectiveDate]); -- để tránh bị duplicate request gây hỏng data logic

CREATE TABLE [cargo_type_price] (
    [cargoTypePriceId] INT IDENTITY(1,1) PRIMARY KEY,
    [cargoTypeId] INT NOT NULL,
    [unit] NVARCHAR(50) NOT NULL, -- kg, m3, chiếc...
    [pricePerUnit] DECIMAL(15, 2) NOT NULL,
    [startEffectiveDate] DATETIME NOT NULL,
    [endEffectiveDate] DATETIME NOT NULL,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,
    FOREIGN KEY ([cargoTypeId]) REFERENCES [cargo_type] ([cargoTypeId]),

	CONSTRAINT CK_CargoPrice_Value CHECK ([pricePerUnit] >= 0),
    CONSTRAINT CK_CargoPrice_Dates CHECK ([endEffectiveDate] >= [startEffectiveDate])
);

CREATE TABLE [coach] (
    [coachId] INT IDENTITY(1,1) PRIMARY KEY,
    [routeId] INT NULL,
    [coachTypeId] INT NOT NULL,
    [licensePlate] VARCHAR(20) NOT NULL UNIQUE,
    [status] VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', 
    [manufacturer] NVARCHAR(100) NULL,
    [year] INT NULL,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,
    FOREIGN KEY ([coachTypeId]) REFERENCES [coach_type] ([coachTypeId]),
    FOREIGN KEY ([routeId]) REFERENCES [route] ([routeId]),

	CONSTRAINT CK_Coach_Status CHECK ([status] IN ('ACTIVE', 'HAVE_INCIDENT', 'MAINTENANCE', 'RETIRED'))
);

CREATE TABLE [coach_status_log] (
    [coachStatusLogId] INT IDENTITY(1,1) PRIMARY KEY,
    [coachId] INT NOT NULL,
    [fromStatus] VARCHAR(50) NULL,
    [toStatus] VARCHAR(50) NOT NULL,
    [reason] NVARCHAR(500) NOT NULL,
    [expectedEndAt] DATETIME NULL,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    FOREIGN KEY ([coachId]) REFERENCES [coach]([coachId]),
    CONSTRAINT CK_CoachStatusLog_ToStatus CHECK ([toStatus] IN ('ACTIVE', 'HAVE_INCIDENT', 'MAINTENANCE', 'RETIRED'))
);

CREATE TABLE [seat] (
    [seatId] INT IDENTITY(1,1) PRIMARY KEY,
    [coachId] INT NOT NULL,
    [seatCode] VARCHAR(10) NOT NULL,
    [rowIndex] INT NOT NULL,
    [colIndex] INT NOT NULL,
    [floorIndex] INT NOT NULL,
    [isActive] BIT NOT NULL DEFAULT 1,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,
    FOREIGN KEY ([coachId]) REFERENCES [coach] ([coachId]),

	CONSTRAINT CK_Seat_Coordinates CHECK ([rowIndex] >= 1 AND [colIndex] >= 1)
);

ALTER TABLE [seat] ADD CONSTRAINT UQ_Seat_Matrix UNIQUE ([coachId], [floorIndex], [rowIndex], [colIndex]);
ALTER TABLE [seat] ADD CONSTRAINT UQ_Seat_Code UNIQUE ([coachId], [seatCode]);

-- ============================================================================
-- LEVEL 3: OPERATIONAL ENTITIES (Chuyến xe thực tế chạy)
-- ============================================================================

CREATE TABLE [trip] (
    [tripId] INT IDENTITY(1,1) PRIMARY KEY,
    [routeId] INT NOT NULL,
    [coachId] INT NOT NULL,
    [departureTime] DATETIME NOT NULL,
    [status] VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED', 
    [driverId] INT NULL,
    [attendantId] INT NULL,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,
    FOREIGN KEY ([routeId]) REFERENCES [route] ([routeId]),
    FOREIGN KEY ([coachId]) REFERENCES [coach] ([coachId]),
    FOREIGN KEY ([driverId]) REFERENCES [staff] ([staffId]),
    FOREIGN KEY ([attendantId]) REFERENCES [staff] ([staffId]),

	CONSTRAINT CK_Trip_Status CHECK ([status] IN ('SCHEDULED', 'IN_PROGRESS', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT CK_Trip_Personnel CHECK ([driverId] <> [attendantId])
);

CREATE TABLE [trip_seat] (
    [tripSeatId] INT IDENTITY(1,1) PRIMARY KEY,
    [tripId] INT NOT NULL,
    [seatId] INT NOT NULL,
    [price] DECIMAL(15,2) NOT NULL, -- Lưu giá tiền CỦA từng ghế của CHUYẾN NÀY (snapshot)
    [status] VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,
    FOREIGN KEY ([tripId]) REFERENCES [trip] ([tripId]),
    FOREIGN KEY ([seatId]) REFERENCES [seat] ([seatId]),

    CONSTRAINT UQ_Trip_Seat UNIQUE ([tripId], [seatId]), -- Đảm bảo 1 chuyến không duplicate ghế
    CONSTRAINT CK_TripSeat_Status CHECK ([status] IN ('AVAILABLE', 'LOCKED', 'SOLD'))
);

-- ============================================================================
-- LEVEL 4: TRANSACTIONAL ENTITIES (Vé và Đơn hàng ký gửi tổng)
-- ============================================================================

CREATE TABLE [passenger_ticket] (
    [passengerTicketId] INT IDENTITY(1,1) PRIMARY KEY,
    [customerId] INT NULL, -- Khách vãng lai
    [tripId] INT NOT NULL,
    [soldBy] INT NULL, -- ID Staff bán tại quầy
    [ticketCode] VARCHAR(50) NOT NULL UNIQUE,
    [totalPrice] DECIMAL(15, 2) NOT NULL,
    [voucherId] INT NULL,
    [pickupStopId] INT NOT NULL,
    [dropoffStopId] INT NOT NULL,
    [pickupStopName] NVARCHAR(255) NOT NULL,  --snapshot
    [dropoffStopName] NVARCHAR(255) NOT NULL, --snapshot
    [voucherCodeSnapshot] VARCHAR(50) NULL, --snapshot
    [refundPolicyDepartureTime] DATETIME NULL, --snapshot: mốc giờ KH để tính tier hoàn tiền
    [majorChangeType] VARCHAR(20) NULL, -- quota: TRANSFER_TRIP | CANCEL_PARTIAL
    [status] VARCHAR(50) NOT NULL DEFAULT 'PENDING', 
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,
    FOREIGN KEY ([customerId]) REFERENCES [customer] ([customerId]),
    FOREIGN KEY ([tripId]) REFERENCES [trip] ([tripId]),
    FOREIGN KEY ([soldBy]) REFERENCES [staff] ([staffId]),
    FOREIGN KEY ([voucherId]) REFERENCES [voucher] ([voucherId]) ON DELETE SET NULL,
    FOREIGN KEY ([pickupStopId]) REFERENCES [coach_stop] ([stopPointId]),
    FOREIGN KEY ([dropoffStopId]) REFERENCES [coach_stop] ([stopPointId]),

	CONSTRAINT CK_PassengerTicket_Price CHECK ([totalPrice] >= 0),
    CONSTRAINT CK_PassengerTicket_Status CHECK ([status] IN ('PENDING', 'CONFIRMED', 'CHANGED', 'CANCELLED')),
    CONSTRAINT CK_PassengerTicket_MajorChangeType CHECK ([majorChangeType] IS NULL OR [majorChangeType] IN ('TRANSFER_TRIP', 'CANCEL_PARTIAL')),
    CONSTRAINT CK_PassengerTicket_Route CHECK ([pickupStopId] <> [dropoffStopId])
);

CREATE TABLE [cargo_ticket] (
    [cargoTicketId] INT IDENTITY(1,1) PRIMARY KEY,
    [tripId] INT NULL, -- được NULLABLE ban đầu vì chưa gán chuyến ngay
    [customerId] INT NULL,
    [senderName] NVARCHAR(100) NOT NULL,
    [senderPhone] VARCHAR(20) NOT NULL,
    [receiverName] NVARCHAR(100) NOT NULL,
    [receiverPhone] VARCHAR(20) NOT NULL,
    [ticketCode] VARCHAR(50) NOT NULL UNIQUE,
    [totalPrice] DECIMAL(15, 2) NOT NULL,
    [description] NVARCHAR(MAX) NULL,
    [feePayer] VARCHAR(20) NOT NULL, 
    [codAmount] DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    [pickupStopId] INT NOT NULL,
    [dropoffStopId] INT NOT NULL,
    [status] VARCHAR(50) NOT NULL DEFAULT 'RECEIVED', 
    [soldBy] INT NOT NULL, -- luôn đc 1 nhân viên tạo đơn cho
    [loadedBy] INT NULL,
    [unloadedBy] INT NULL,
    [deliveredBy] INT NULL,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,
    FOREIGN KEY ([tripId]) REFERENCES [trip] ([tripId]),
    FOREIGN KEY ([customerId]) REFERENCES [customer] ([customerId]),
    FOREIGN KEY ([soldBy]) REFERENCES [staff] ([staffId]),
    FOREIGN KEY ([loadedBy]) REFERENCES [staff] ([staffId]),
    FOREIGN KEY ([unloadedBy]) REFERENCES [staff] ([staffId]),
    FOREIGN KEY ([deliveredBy]) REFERENCES [staff] ([staffId]),

	CONSTRAINT CK_CargoTicket_Prices CHECK ([totalPrice] >= 0 AND [codAmount] >= 0),
    CONSTRAINT CK_CargoTicket_Payer CHECK ([feePayer] IN ('SENDER', 'RECEIVER')),
    CONSTRAINT CK_CargoTicket_Status CHECK ([status] IN ('RECEIVED', 'LOADED', 'ARRIVED', 'DELIVERED', 'CANCELLED', 'REJECTED', 'ABANDONED')),
    CONSTRAINT CK_CargoTicket_Route CHECK ([pickupStopId] <> [dropoffStopId])
);

-- ============================================================================
-- LEVEL 5: SUB-DETAILS & FINANCIALS (Chi tiết vé, chi tiết hàng, thanh toán)
-- ============================================================================

CREATE TABLE [passenger_ticket_detail] (
    [ticketDetailId] INT IDENTITY(1,1) PRIMARY KEY,
    [passengerTicketId] INT NOT NULL,
    [tripSeatId] INT NULL,
    [seatCodeSnapshot] VARCHAR(10) NOT NULL,
    [qrcode] VARCHAR(MAX) NULL,
    [fullName] NVARCHAR(100) NOT NULL,
    [phone] VARCHAR(20) NOT NULL,
    [email] VARCHAR(100) NULL,
    [price] DECIMAL(15, 2) NOT NULL,
    [status] VARCHAR(50) NOT NULL DEFAULT 'PENDING', 
    [expiredAt] DATETIME NULL,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,
    FOREIGN KEY ([passengerTicketId]) REFERENCES [passenger_ticket] ([passengerTicketId]) ON DELETE CASCADE,
    FOREIGN KEY ([tripSeatId]) REFERENCES [trip_seat] ([tripSeatId]) ON DELETE SET NULL,

	CONSTRAINT CK_PassengerDetail_Price CHECK ([price] >= 0),
    CONSTRAINT CK_PassengerDetail_Status CHECK ([status] IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'CANCELLED', 'EXPIRED'))
);

CREATE TABLE [cargo_ticket_detail] (
    [cargoTicketDetailId] INT IDENTITY(1,1) PRIMARY KEY,
    [cargoTicketId] INT NOT NULL,
    [cargoTypePriceId] INT NOT NULL,
    [description] NVARCHAR(MAX) NULL,
    [quantity] INT NOT NULL DEFAULT 1,
    [weightKg] DECIMAL(8, 2) NOT NULL DEFAULT 0.00,
    [dimensionVol] DECIMAL(8, 2) NOT NULL DEFAULT 0.00,
    [calculatedPrice] DECIMAL(15, 2) NOT NULL,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,
    FOREIGN KEY ([cargoTicketId]) REFERENCES [cargo_ticket] ([cargoTicketId]) ON DELETE CASCADE,
    FOREIGN KEY ([cargoTypePriceId]) REFERENCES [cargo_type_price] ([cargoTypePriceId]),

	CONSTRAINT CK_CargoDetail_Quantity CHECK ([quantity] >= 1),
    CONSTRAINT CK_CargoDetail_Metrics CHECK ([weightKg] >= 0 AND [dimensionVol] >= 0),
    CONSTRAINT CK_CargoDetail_Price CHECK ([calculatedPrice] >= 0)
);

CREATE TABLE [payment] (
    [paymentId] INT IDENTITY(1,1) PRIMARY KEY,
    [passengerTicketId] INT NULL,
    [cargoTicketId] INT NULL,
    [amount] DECIMAL(15, 2) NOT NULL,
    [paymentMethod] VARCHAR(50) NOT NULL, 
    [transactionId] VARCHAR(100) NOT NULL,
    [status] VARCHAR(50) NOT NULL DEFAULT 'PENDING', 
    [refundAmount] DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    [paymentTime] DATETIME NULL,
    [callbackData] NVARCHAR(MAX) NULL,
    [cancelToken] VARCHAR(255) NULL,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,
    FOREIGN KEY ([passengerTicketId]) REFERENCES [passenger_ticket] ([passengerTicketId]),
    FOREIGN KEY ([cargoTicketId]) REFERENCES [cargo_ticket] ([cargoTicketId]),
	
	CONSTRAINT CK_Payment_Target CHECK (
        ([passengerTicketId] IS NOT NULL AND [cargoTicketId] IS NULL) OR 
        ([passengerTicketId] IS NULL AND [cargoTicketId] IS NOT NULL)
    ),
	CONSTRAINT CK_Payment_Amount CHECK ([amount] > 0 AND [refundAmount] >= 0),
    CONSTRAINT CK_Payment_Method CHECK ([paymentMethod] IN ('CASH', 'BANK_TRANSFER')),
    CONSTRAINT CK_Payment_Status CHECK ([status] IN ('PENDING', 'COMPLETED', 'FAILED')),
);

-- ============================================================================
-- LEVEL 6: SUB-DEPENDENTS (Trẻ em đi kèm và Lệnh hoàn tiền)
-- ============================================================================

CREATE TABLE [accompanied_child] (
    [accompaniedChildId] INT IDENTITY(1,1) PRIMARY KEY,
    [ticketDetailId] INT NOT NULL,
    [fullname] NVARCHAR(100) NOT NULL,
    [birthYear] INT NOT NULL,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,
    FOREIGN KEY ([ticketDetailId]) REFERENCES [passenger_ticket_detail] ([ticketDetailId]) ON DELETE CASCADE,
	
	-- 1 trẻ em chỉ đc ngồi cùng ghế 1 ng lớn
	CONSTRAINT UQ_AccompaniedChild_TicketDetail UNIQUE ([ticketDetailId]),
    CONSTRAINT CK_AccompaniedChild_BirthYear CHECK ([birthYear] >= 1900 AND [birthYear] <= YEAR(GETDATE()))
);

CREATE TABLE [refund] (
    [refundId] INT IDENTITY(1,1) PRIMARY KEY,
    [paymentId] INT NOT NULL,
    [amount] DECIMAL(15, 2) NOT NULL,
    [reason] NVARCHAR(MAX) NULL,
    [refundMethod] VARCHAR(50) NOT NULL,
    [transactionId] VARCHAR(100) NULL,
    [status] VARCHAR(50) NOT NULL DEFAULT 'PENDING', 
    [refundTime] DATETIME NULL,
    [callbackData] NVARCHAR(MAX) NULL,
    [createdAt] DATETIME DEFAULT GETDATE(),
    [createdBy] INT NULL,
    [updatedAt] DATETIME DEFAULT GETDATE(),
    [updatedBy] INT NULL,
    FOREIGN KEY ([paymentId]) REFERENCES [payment] ([paymentId]),

	CONSTRAINT CK_Refund_Amount CHECK ([amount] > 0),
    CONSTRAINT CK_Refund_Method CHECK ([refundMethod] IN ('BANK_TRANSFER', 'CASH')),
    CONSTRAINT CK_Refund_Status CHECK ([status] IN ('PENDING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE [refresh_token] (
[id] INT IDENTITY(1,1) PRIMARY KEY,
[token] NVARCHAR(512) NOT NULL UNIQUE,
[accountId] INT NOT NULL,
[expiresAt] DATETIME NOT NULL,
[isRevoked] BIT NOT NULL DEFAULT 0,
FOREIGN KEY ([accountId]) REFERENCES [account]([accountId])
);


USE VeXeDB;
GO
