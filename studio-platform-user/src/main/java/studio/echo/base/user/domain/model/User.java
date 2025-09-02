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
 *      @file User.java
 *      @date 2025
 *
 */

package studio.echo.base.user.domain.model;

import java.time.Instant;

import studio.echo.platform.domain.model.PropertyAware;

public interface User extends PropertyAware {

    public Long getUserId();

    public String getUsername();

    public String getName();

    public String getFirstName();

    public String getLastName();

    public boolean isEnabled();

    public boolean isNameVisible();

    public boolean isEmailVisible();

    public Status getStatus();

    public String getPassword();

    public boolean isAnonymous();

    public String getEmail();

    public Instant getCreationDate();

    public Instant getModifiedDate();

    public abstract boolean isExternal();

    public abstract boolean isAvatarExists();

    public abstract void setAvatarExists(boolean avatarExists);

}
