package com.hckcapital.be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** See ProfileService.updateAccountType — the Settings > Privacy toggle's own request body. */
@Data
public class UpdateAccountTypeRequest {

    @NotBlank
    @Pattern(regexp = "PUBLIC|PRIVATE", message = "accountType must be PUBLIC or PRIVATE")
    private String accountType;
}
