package com.billngshop.BILLING.service;

import com.billngshop.BILLING.entity.UserAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 60) String username,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,72}$",
                    message = "Password must be 10-72 characters and include upper, lower, number, and symbol")
            String password,
            UserAccount.Role role,
            @Size(max = 80) String firstName,
            @Size(max = 80) String lastName,
            @Size(max = 24) String mobileNumber) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record PasswordResetRequest(@NotBlank @Email String email) {
    }

    public record PasswordResetConfirm(
            @NotBlank String token,
            @NotBlank @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,72}$")
            String newPassword) {
    }

    public record ProductRequest(
            @NotBlank @Size(max = 80) String sku,
            @NotBlank @Size(max = 180) String name,
            Long categoryId,
            Long supplierId,
            @NotNull @DecimalMin("0.00") BigDecimal sellingPrice,
            @NotNull @DecimalMin("0.00") BigDecimal costPrice,
            @Min(0) int stockQuantity,
            @Min(0) int reorderLevel,
            @Size(max = 2048) @Pattern(regexp = "^(https?://[^\\s]+)?$",
                    message = "imageUrl must be an http or https URL") String imageUrl) {
    }

    public record CategoryRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record CustomerRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 40) String phone,
            @Email @Size(max = 160) String email) {
    }

    public record SupplierRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 160) String contact,
            @Email @Size(max = 160) String email,
            boolean preferred) {
    }

    public record StockAdjustment(
            @NotNull Long productId,
            @NotNull Integer quantityChange,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record StockCountRequest(@NotNull Long productId, @Min(0) int countedQuantity) {
    }

    public record InvoiceLineRequest(@NotNull Long productId, @Min(1) int quantity) {
    }

    public record PaymentRequest(
            @NotBlank @Pattern(regexp = "(?i)cash|card|upi") String mode,
            @NotNull @DecimalMin("0.01") BigDecimal amount) {
    }

    public record InvoiceRequest(
            Long customerId,
            @Size(max = 40) String tillId,
            @NotEmpty List<@Valid InvoiceLineRequest> lines,
            @DecimalMin("0.00") BigDecimal discount,
            List<@Valid PaymentRequest> payments) {
    }

    public record VoidRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record ReturnRequest(@NotEmpty List<@Valid InvoiceLineRequest> lines,
                                @NotBlank @Pattern(regexp = "(?i)cash|card|upi") String refundMode,
                                @NotBlank @Size(max = 500) String reason) {
    }

    public record PurchaseLineRequest(
            @NotNull Long productId,
            @Min(1) int quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitCost) {
    }

    public record PurchaseRequest(@NotNull Long supplierId,
                                  @NotEmpty List<@Valid PurchaseLineRequest> lines) {
    }

    public record ReceiveRequest(@NotNull Long productId, @Min(1) int quantity) {
    }

    public record ShiftOpenRequest(@NotBlank @Size(max = 40) String tillId,
                                   @NotNull @DecimalMin("0.00") BigDecimal openingCash) {
    }

    public record ShiftCloseRequest(@NotNull @DecimalMin("0.00") BigDecimal closingCash) {
    }

    public record ExpenseRequest(@NotNull @DecimalMin("0.01") BigDecimal amount,
                                 @NotBlank @Pattern(regexp = "(?i)cash|card|upi") String paymentMode,
                                 @NotBlank @Size(max = 40) String tillId,
                                 @NotBlank @Size(max = 500) String description) {
    }

    public record ReconcileRequest(@NotNull LocalDate date, @NotBlank @Size(max = 40) String tillId,
                                   @NotNull @DecimalMin("0.00") BigDecimal declaredCash) {
    }

    public record SalesTargetRequest(@NotNull LocalDate date,
                                     @NotNull @DecimalMin("0.00") BigDecimal amount) {
    }

    public record SettingRequest(@NotBlank @Size(max = 2000) String value) {
    }

    public record SettingValue(String key, String value) {
    }
}
