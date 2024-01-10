package com.bookmysport.authentication_service.UserServices;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import com.bookmysport.authentication_service.Models.EmailModel;
import com.bookmysport.authentication_service.Models.LoginModel;
import com.bookmysport.authentication_service.Models.OtpUserModel;
import com.bookmysport.authentication_service.Models.ResponseMessage;
import com.bookmysport.authentication_service.Models.TwoFA;
import com.bookmysport.authentication_service.Models.UserModel;
import com.bookmysport.authentication_service.Repository.UserRepository;
import com.bookmysport.authentication_service.StaticInfo.OTPGenerator;
import com.bookmysport.authentication_service.StaticInfo.StaticVariables;

@Service
public class UserService {

    @Autowired
    UserRepository userRepository;

    @Autowired
    private ResponseMessage responseMessage;

    @Autowired
    private AuthService authService;

    @Autowired
    private TwoFA twoFA;

    @Autowired
    private EmailModel emailModel;

    @Autowired
    private EmailService emailService;

    @Autowired
    private OtpUserModel otpUserModel;

    public String hashPassword(String password) {
        String strong_salt = BCrypt.gensalt(10);
        String encyptedPassword = BCrypt.hashpw(password, strong_salt);
        return encyptedPassword;
    }

    public void otpExpiry(String otpExpiryState) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        if (otpExpiryState.equals("passwordReset")) {
            scheduler.schedule(() -> {
                otpUserModel.setOtp(0);
            }, 5, TimeUnit.MINUTES);
        } else {
            twoFA.setOtp(0);
        }
    }

    public ResponseEntity<Object> generateOTPforTwoFAService(UserModel userModel) {
        try {
            int otpForTwoFA = OTPGenerator.generateRandom6DigitNumber();
            twoFA.setOtp(otpForTwoFA);
            emailModel.setRecipient(userModel.getEmail());
            emailModel.setSubject("OTP for Two-Factor Authentication");
            emailModel.setMsgBody("Your OTP for Two-Factor Authentication is " + Integer.toString(otpForTwoFA)
                    + ". It is valid only for 5 minutes.");

            String response = emailService.sendSimpleMail(emailModel);
            responseMessage.setSuccess(true);
            responseMessage.setMessage(response);
            return ResponseEntity.ok().body(responseMessage);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error!");
        }
    }

    public ResponseEntity<Object> userRegisterService(UserModel userModel, BindingResult bindingResult) {
        try {
            if (!bindingResult.hasErrors()) {
                if (bindingResult.hasFieldErrors()) {
                    responseMessage.setSuccess(false);
                    responseMessage.setMessage("Enter valid Email");
                    return ResponseEntity.badRequest().body(responseMessage);
                }
                UserModel userByEmail = userRepository.findByEmail(userModel.getEmail());
                if (userByEmail == null) {
                    userModel.setPassword(hashPassword(userModel.getPassword()));
                    userRepository.save(userModel);
                    responseMessage.setSuccess(true);
                    responseMessage.setMessage("Account Created Successfully!");
                    responseMessage.setToken(authService.generateToken(userModel.getEmail()));
                    return ResponseEntity.badRequest().body(responseMessage);
                } else {
                    responseMessage.setSuccess(false);
                    responseMessage.setMessage("User with this email already exists!");
                    responseMessage.setToken(null);
                    return ResponseEntity.badRequest().body(responseMessage);
                }
            } else {
                responseMessage.setSuccess(false);
                responseMessage.setMessage("Invalid user name or email");
                return ResponseEntity.badRequest().body(responseMessage);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error!");
        }
    }

    public ResponseEntity<Object> userLoginService(LoginModel loginModel) {
        try {
            UserModel userModel = userRepository.findByEmail(loginModel.getEmail());
            if (userModel != null) {
                if (BCrypt.checkpw(loginModel.getPassword(), userModel.getPassword())) {
                    StaticVariables.loginStatus = true;
                    responseMessage.setSuccess(true);
                    responseMessage.setMessage("Logged in Successfully!");
                    responseMessage.setToken(null);
                    twoFA.setEmail(userModel.getEmail());
                    generateOTPforTwoFAService(userModel);
                    return ResponseEntity.ok().body(responseMessage);
                } else {
                    responseMessage.setSuccess(false);
                    responseMessage.setMessage("Invalid email or password");
                    responseMessage.setToken(null);
                    return ResponseEntity.badRequest().body(responseMessage);
                }
            } else {
                responseMessage.setSuccess(false);
                responseMessage.setMessage("Invalid email or password");
                return ResponseEntity.badRequest().body(responseMessage);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error!");
        }
    }

    public ResponseEntity<Object> TwoFAService(int otpforTwoFAFromUser) {
        try {
            if (twoFA.getOtp() == otpforTwoFAFromUser) {
                responseMessage.setSuccess(true);
                responseMessage.setMessage("Login Successfully!");
                responseMessage.setToken(authService.generateToken(twoFA.getEmail()));
                return ResponseEntity.ok().body(responseMessage);
            } else {
                responseMessage.setSuccess(false);
                responseMessage.setMessage("Invalid OTP.");
                return ResponseEntity.badRequest().body(responseMessage);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error!");
        }
    }

    public ResponseEntity<Object> forgotPasswordService(String email) {
        return sendingEmailService(email, otpUserModel);
    }

    public ResponseEntity<Object> sendingEmailService(String email, OtpUserModel otpUserModel) {
        try {
            UserModel userByEmail = userRepository.findByEmail(email);
            if (userByEmail != null) {
                otpUserModel.setEmail(email);
                int otp = OTPGenerator.generateRandom6DigitNumber();
                otpUserModel.setOtp(otp);

                emailModel.setRecipient(email);
                emailModel.setSubject("OTP for Resetting your password");
                emailModel.setMsgBody("Your OTP for resetting your password is " + Integer.toString(otp)
                        + ". It is valid only for 5 minutes.");

                String response = emailService.sendSimpleMail(emailModel);
                otpExpiry("passwordReset");
                responseMessage.setSuccess(true);
                responseMessage.setMessage(response);
                return ResponseEntity.ok().body(responseMessage);
            } else {
                responseMessage.setSuccess(false);
                responseMessage.setMessage("Invalid Email");
                return ResponseEntity.badRequest().body(responseMessage);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error!");
        }
    }

    public ResponseEntity<Object> verifyTheOtpEnteredByUser(String otpFromUser) {
        try {
            if (otpFromUser.equals(Integer.toString(otpUserModel.getOtp()))) {
                responseMessage.setSuccess(true);
                responseMessage.setMessage("OTP Verified");
                return ResponseEntity.ok().body(responseMessage);

            } else {
                responseMessage.setSuccess(false);
                responseMessage.setMessage("Invalid OTP, check your registered Email to get the 6-digit OTP");

                return ResponseEntity.badRequest().body(responseMessage);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error!");
        }
    }

    public ResponseEntity<Object> resetThePasswordService(String passwordFromUser) {
        try {
            UserModel user = userRepository.findByEmail(otpUserModel.getEmail());
            user.setPassword(hashPassword(passwordFromUser));
            userRepository.save(user);

            responseMessage.setSuccess(true);
            responseMessage.setMessage("Password Changed Successfully");
            return ResponseEntity.ok().body(responseMessage);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error!");
        }
    }
}