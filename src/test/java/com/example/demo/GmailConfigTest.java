package com.example.demo;

import com.example.demo.services.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:gmail-config",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
    "GMAIL_USERNAME=remetente@example.com", "GMAIL_APP_PASSWORD=senha-apenas-de-teste",
    "IMOBILIARIA_EMAIL=imobiliaria@example.com"
})
@ActiveProfiles("gmail")
class GmailConfigTest {
    @Autowired Environment environment;
    @Autowired EmailService email;
    @MockitoBean JavaMailSender sender;

    @Test void perfilGmailUsaSmtpTlsRemetenteEDestinatarioConfigurados() {
        assertEquals("smtp.gmail.com", environment.getProperty("spring.mail.host"));
        assertEquals("587", environment.getProperty("spring.mail.port"));
        assertEquals("true", environment.getProperty("spring.mail.properties.mail.smtp.starttls.required"));
        assertEquals("false", environment.getProperty("spring.mail.properties.mail.debug"));
        assertEquals("smtp", environment.getProperty("app.mail.transport"));
        email.enviarTexto(environment.getProperty("app.mail.contato"), "Interesse no imóvel #1", "Mensagem de demonstração");
        var captura = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captura.capture());
        assertEquals("remetente@example.com", captura.getValue().getFrom());
        assertArrayEquals(new String[]{"imobiliaria@example.com"}, captura.getValue().getTo());
    }
}
