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
 *      @file AttachmentService.java
 *      @date 2025
 *
 */

package studio.one.application.attachment.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.platform.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AttachmentService {

	public static final String SERVICE_NAME = "components:attachment-service";

	Attachment getAttachmentById(long attachmentId) throws NotFoundException;

	List<Attachment> getAttachments(int objectType, long objectId);

	Page<Attachment> findAttachemnts(Pageable pageable);

	Page<Attachment> findAttachemnts(int objectType, long objectId, Pageable pageable);

	Attachment createAttachment(int objectType, long objectId, String name, String contentType, File file);

	Attachment createAttachment(int objectType, long objectId, String name, String contentType, InputStream inputStream);

	Attachment createAttachment(int objectType, long objectId, String name, String contentType, InputStream inputStream, int size);

	Attachment saveAttachment(Attachment attachment);

	void removeAttachment(Attachment attachment);

	InputStream getInputStream(Attachment attachment) throws IOException;

}
