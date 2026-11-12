package com.prgate.users.infraestructure.github;
import org.junit.jupiter.api.Test;
import com.prgate.users.application.github.GitHubLinkException;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
class GitHubTokenCipherTest {
    GitHubTokenCipher cipher=new GitHubTokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
    @Test void encryptsWithUniqueNoncesAndBindsCiphertextToItsOwner(){
        String a=cipher.encrypt("fixture-token","student:42"),b=cipher.encrypt("fixture-token","student:42");
        assertNotEquals(a,b); assertFalse(a.contains("fixture-token"));
        assertEquals("fixture-token",cipher.decrypt(a,"student:42"));
        assertThrows(GitHubLinkException.class,()->cipher.decrypt(a,"other:42"));
    }
    @Test void rejectsTamperedCiphertextAndMissingKey(){
        String encrypted=cipher.encrypt("fixture-token","student:42");
        assertThrows(GitHubLinkException.class,()->cipher.decrypt(encrypted.substring(0,encrypted.length()-4)+"AAAA","student:42"));
        assertThrows(GitHubLinkException.class,()->new GitHubTokenCipher("").encrypt("fixture-token","student:42"));
        assertThrows(IllegalStateException.class,()->new GitHubTokenCipher("invalid"));
    }
}
