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
 *      @file GroupNotFoundException.java
 *      @date 2025
 *
 */
package studio.echo.base.user.exception;

import org.springframework.http.HttpStatus;

import studio.echo.platform.error.ErrorType;
import studio.echo.platform.exception.NotFoundException;

@SuppressWarnings({ "serial", "java:S110"}) 
public class GroupNotFoundException extends NotFoundException {
 
    private static final ErrorType BY_NAME = ErrorType.of("error.group.not.found.name", HttpStatus.NOT_FOUND);

    private static final ErrorType BY_ID =  ErrorType.of("error.group.not.found.id", HttpStatus.NOT_FOUND);

    public GroupNotFoundException(String name) {
        super(BY_NAME, "Group Not Found", name); 
    }
    
    public GroupNotFoundException(Long groupId) {
        super(BY_ID, "Group Not Found", groupId); 
    }
    
    public static GroupNotFoundException byId(Long groupId) {
        return new GroupNotFoundException(groupId);
    }

    public static GroupNotFoundException byName(String name) {
        return new GroupNotFoundException(name);
    }
}
