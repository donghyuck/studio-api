/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file MailServiceImpl.java
 *      @date 2025
 *
 */

package studio.one.aplication.security.auth.password.impl;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import lombok.RequiredArgsConstructor;
import studio.one.aplication.security.auth.password.MailService;

/**
 *
 * @author  donghyuck, son
 * @since 2025-12-05
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-12-05  donghyuck, son: 최초 생성.
 * </pre>
 */

@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final String resetPasswordUrl;
    private final JavaMailSender mailSender;

    @Override
    public void sendPasswordResetMail(String to, String token) {
        String link = resetPasswordUrl + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        String subject = "[MyApp] 비밀번호 재설정 안내";
        String content = """
                비밀번호 재설정을 요청하셨습니다.
                아래 링크를 클릭하여 비밀번호를 재설정해주세요.

                %s

                이 링크는 30분 동안만 유효합니다.
                본인이 요청하지 않은 경우 이 메일을 무시하셔도 됩니다.
                """.formatted(link);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content); 
        mailSender.send(message);
    }
}
