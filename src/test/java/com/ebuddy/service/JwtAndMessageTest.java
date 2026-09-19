package com.ebuddy.service;
import com.ebuddy.domain.*; import com.ebuddy.security.JwtService; import org.junit.jupiter.api.*; import java.time.Duration; import java.util.UUID; import static org.junit.jupiter.api.Assertions.*;
class JwtAndMessageTest {
 @Test void jwtRoundTrip(){var s=new JwtService("12345678901234567890123456789012",Duration.ofHours(1));UUID id=UUID.randomUUID();assertEquals(id,s.subject(s.create(id)));}
 @Test void messageStatusIsMonotonic(){var m=new MessageEntity(UUID.randomUUID(),UUID.randomUUID(),"c1","hi");assertEquals(MessageState.sent,m.getStatus());m.markDelivered();assertEquals(MessageState.delivered,m.getStatus());m.markRead();assertEquals(MessageState.read,m.getStatus());m.markDelivered();assertEquals(MessageState.read,m.getStatus());assertNotNull(m.getDeliveredAt());assertNotNull(m.getReadAt());}
}
