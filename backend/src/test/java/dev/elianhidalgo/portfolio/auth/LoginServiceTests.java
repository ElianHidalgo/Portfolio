package dev.elianhidalgo.portfolio.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.elianhidalgo.portfolio.auth.application.InvalidCredentialsException;
import dev.elianhidalgo.portfolio.auth.application.LoginService;
import dev.elianhidalgo.portfolio.auth.application.TokenService;
import dev.elianhidalgo.portfolio.auth.domain.AdminAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class LoginServiceTests {

    @Test
    void unknownUserStillPaysTheBcryptCost() {
        var accounts = mock(AdminAccountRepository.class);
        var encoder = mock(PasswordEncoder.class);
        when(accounts.findByUsername("nobody")).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("$2a$10$dummy");
        var service = new LoginService(accounts, encoder, mock(TokenService.class));

        assertThatThrownBy(() -> service.login("nobody", "pw")).isInstanceOf(InvalidCredentialsException.class);
        // Same work as for a real account, so response time does not reveal which usernames exist.
        verify(encoder).matches(eq("pw"), anyString());
    }
}
