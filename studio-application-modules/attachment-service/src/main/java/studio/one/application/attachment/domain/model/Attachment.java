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
 *      @file Attachment.java
 *      @date 2025
 *
 */

package studio.one.application.attachment.domain.model;

import java.time.Instant;
import java.util.Map;

import studio.one.platform.domain.model.PropertyAware;
import studio.one.platform.domain.model.TypeObject;

public interface Attachment extends PropertyAware, TypeObject {

	public long getAttachmentId();

	public void setAttachmentId(long attachementId);

	public String getName();

	public void setName(String name);

	public long getSize();

	public void setSize(long size);

	public String getContentType();

	public void setContentType(String contentType);

	public Map<String, String> getProperties();

	public void setProperties(Map<String, String> properties);

	public abstract long getCreatedBy();

	public abstract void setCreatedBy(long userId);

	public Instant getCreatedAt();

	public Instant getUpdatedAt();

}
