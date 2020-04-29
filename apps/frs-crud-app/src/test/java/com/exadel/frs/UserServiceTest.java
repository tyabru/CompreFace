package com.exadel.frs;

import com.exadel.frs.dto.ui.UserCreateDto;
import com.exadel.frs.dto.ui.UserUpdateDto;
import com.exadel.frs.entity.User;
import com.exadel.frs.exception.EmailAlreadyRegisteredException;
import com.exadel.frs.exception.EmptyRequiredFieldException;
import com.exadel.frs.exception.InvalidEmailException;
import com.exadel.frs.exception.RegistrationTokenExpiredException;
import com.exadel.frs.exception.UserDoesNotExistException;
import com.exadel.frs.helpers.EmailSender;
import com.exadel.frs.repository.UserRepository;
import com.exadel.frs.service.UserService;
import liquibase.integration.spring.SpringLiquibase;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.MockBeans;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {
    private final String EXPIRED_TOKEN = "expired_token";

    private UserRepository userRepositoryMock;
    private UserService userService;
    private EmailSender emailSenderMock;

    UserServiceTest() {
        userRepositoryMock = mock(UserRepository.class);
        emailSenderMock = mock(EmailSender.class);
        userService = new UserService(userRepositoryMock, PasswordEncoderFactories.createDelegatingPasswordEncoder(), emailSenderMock);
        userService.setEnv(new MockEnvironment());
    }

    @Test
    void successGetUser() {
        Long userId = 1L;

        User user = User.builder().id(userId).build();

        when(userRepositoryMock.findById(anyLong())).thenReturn(Optional.of(user));

        User result = userService.getUser(userId);

        assertThat(result.getId(), is(userId));
    }

    @Test
    void failGetUser() {
        Long userId = 1L;

        when(userRepositoryMock.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(UserDoesNotExistException.class, () -> userService.getUser(userId));
    }

    @Test
    void successCreateUser() {
        UserCreateDto userCreateDto = UserCreateDto.builder()
                .email("email@example.com")
                .password("password")
                .firstName("firstName")
                .lastName("lastName")
                .build();

        userService.createUser(userCreateDto);

        verify(emailSenderMock).sendMail(anyString(), anyString(), anyString());
        verify(userRepositoryMock).save(any(User.class));
    }

    @Test
    void failCreateUserEmptyPassword() {
        UserCreateDto userCreateDto = UserCreateDto.builder()
                .email("email@example.com")
                .password("")
                .firstName("firstName")
                .lastName("lastName")
                .build();

        assertThrows(EmptyRequiredFieldException.class, () -> userService.createUser(userCreateDto));
    }

    @Test
    void failCreateUserEmptyEmail() {
        UserCreateDto userCreateDto = UserCreateDto.builder()
                .email("")
                .password("password")
                .firstName("firstName")
                .lastName("lastName")
                .build();

        assertThrows(EmptyRequiredFieldException.class, () -> userService.createUser(userCreateDto));
    }

    @Test
    void failCreateUserDuplicateEmail() {
        UserCreateDto userCreateDto = UserCreateDto.builder()
                .email("email@example.com")
                .password("password")
                .firstName("firstName")
                .lastName("lastName")
                .build();

        when(userRepositoryMock.existsByEmail(anyString())).thenReturn(true);

        assertThrows(EmailAlreadyRegisteredException.class, () -> userService.createUser(userCreateDto));
    }

    @Test
    void successUpdateUser() {
        Long userId = 1L;

        User repoUser = User.builder()
                .id(userId)
                .email("email")
                .password("password")
                .firstName("firstName")
                .lastName("lastName")
                .build();

        UserUpdateDto userUpdateDto = UserUpdateDto.builder()
                .password("password")
                .firstName("firstName")
                .lastName("lastName")
                .build();

        when(userRepositoryMock.findById(anyLong())).thenReturn(Optional.of(repoUser));

        userService.updateUser(userUpdateDto, userId);

        verify(userRepositoryMock).save(any(User.class));

        assertThat(repoUser.getPassword(), not(userUpdateDto.getPassword()));
        assertThat(repoUser.getFirstName(), is(userUpdateDto.getFirstName()));
        assertThat(repoUser.getLastName(), is(userUpdateDto.getLastName()));
    }

    @Test
    void successDeleteUser() {
        Long userId = 1L;

        userService.deleteUser(userId);

        verify(userRepositoryMock).deleteById(anyLong());
    }

    @Test
    void cannotCreateNewUserWithIncorrectEmail() {
        val userWithIncorrectEmial = UserCreateDto.builder()
                .email("wrong_email")
                .password("password")
                .firstName("firstName")
                .lastName("lastName")
                .build();

        assertThrows(InvalidEmailException.class, () -> userService.createUser(userWithIncorrectEmial));
    }

    @Test
    void cannotCreateNewUserWithoutFirstName() {
        val userWithoutFirstName = UserCreateDto.builder()
                .email("email@example.com")
                .password("password")
                .firstName(null)
                .lastName("lastName")
                .build();

        assertThrows(EmptyRequiredFieldException.class, () -> userService.createUser(userWithoutFirstName));
    }

    @Test
    void cannotCreateNewUserWithoutLastName() {
        val userWithoutFirstName = UserCreateDto.builder()
                .email("email@example.com")
                .password("password")
                .firstName("firstName")
                .lastName(null)
                .build();

        assertThrows(EmptyRequiredFieldException.class, () -> userService.createUser(userWithoutFirstName));
    }

    @Test
    void confirmRegistrationReturns403WhenTokenIsExpired() {

        final Executable confirmRegistration = () -> userService.confirmRegistration(EXPIRED_TOKEN);

        Assertions.assertThrows(RegistrationTokenExpiredException.class, confirmRegistration);
    }

    @Test
    void confirmRegistrationEnablesUserAndRemovesTokenWhenSuccess() {
        when(userRepositoryMock.save(any())).thenAnswer(returnsFirstArg());
        UserCreateDto userCreateDto = UserCreateDto.builder()
                .email("email@example.com")
                .password("password")
                .firstName("firstName")
                .lastName("lastName")
                .build();

        val createdUser = userService.createUser(userCreateDto);
        assertFalse(createdUser.isEnabled());

        when(userRepositoryMock.findByRegistrationToken(createdUser.getRegistrationToken())).thenReturn(Optional.of(createdUser));
        userService.confirmRegistration(createdUser.getRegistrationToken());
        assertTrue(createdUser.isEnabled());
        assertNull(createdUser.getRegistrationToken());
    }

    @Test
    void createsUserWithLowerCaseEmail() {
        when(userRepositoryMock.save(any())).thenAnswer(returnsFirstArg());
        val userCreateDto = UserCreateDto.builder()
                .email("Email@example.COm")
                .password("password")
                .firstName("firstName")
                .lastName("lastName")
                .build();

        val createdUser = userService.createUser(userCreateDto);

        assertEquals(userCreateDto.getEmail().toLowerCase(), createdUser.getEmail());
    }

    @Nested
    @DisplayName("Tests that use database")
    @ExtendWith(SpringExtension.class)
    @DataJpaTest
    @MockBeans({@MockBean(SpringLiquibase.class), @MockBean(PasswordEncoder.class), @MockBean(EmailSender.class)})
    @Import({UserService.class})
    public class RepositoryEnabledTest {
        private static final String ENABLED_USER_EMAIL = "enabled_user@email.com";
        private static final String DISABLED_USER_EMAIL = "disabled_user@email.com";

        @SpyBean
        private UserService userService;

        @Autowired
        private UserRepository userRepository;

        @Test
        void getEnabledUserByEmailReturnsActiveUser() {
            createAndEnableUser(ENABLED_USER_EMAIL);

            val enabledUser = userService.getEnabledUserByEmail(ENABLED_USER_EMAIL);

            assertNotNull(enabledUser);
            assertTrue(enabledUser.isEnabled());
        }

        @Test
        void getEnabledUserByEmailThrowsExceptionIfUserIsDisabled() {
            createUser(DISABLED_USER_EMAIL);

            val disabledUser = userRepository.findByEmail(DISABLED_USER_EMAIL).get();

            assertNotNull(disabledUser);
            assertFalse(disabledUser.isEnabled());

            assertThrows(UserDoesNotExistException.class, () -> userService.getEnabledUserByEmail(DISABLED_USER_EMAIL));
        }

        private void createAndEnableUser(String email) {
            String regToken = UUID.randomUUID().toString();
            when(userService.generateRegistrationToken()).thenReturn(regToken);
            createUser(email);
            confirmRegistration(regToken);
        }

        private void createUser(String email) {
            val user = UserCreateDto.builder()
                    .email(email)
                    .firstName("first_name")
                    .lastName("last_name")
                    .password("password")
                    .build();

            userService.createUser(user);
        }

        private void confirmRegistration(String regToken) {
            userService.confirmRegistration(regToken);
        }
    }
}
