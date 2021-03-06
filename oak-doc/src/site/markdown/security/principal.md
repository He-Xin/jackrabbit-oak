<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

Principal Management
--------------------------------------------------------------------------------

### Characteristics of the Principal Management Implementation

The default implementation of the principal management API basically corresponds
to the default in Jackrabbit 2.x and is based on the user management implementation.
Note however, that as of Oak only a single principal provider is exposed on the
SPI level (used to be multiple principal providers with the LoginModule configuration
in Jackrabbit 2.x). See the configuration section below for details.

#### Differences wrt Jackrabbit 2.x

See the corresponding [documentation](principal/differences.html).

### API Extensions

- [PrincipalProvider]: SPI level access to principals known to the repository
which is also used by the default implementation of the `PrincipalManager` interface.
This interface replaces the internal `PrincipalProvider` interface present in
Jackrabbit 2.x. Note, that principals from different sources can be supported by
using [CompositePrincipalProvider] or a similar implementation that proxies
different sources.

##### Special Principals
- [AdminPrincipal]: Marker interface to identify the principal associated with administrative user(s).
- [EveryonePrincipal]: built-in group principal implementation that has every other valid principal as member.
- [SystemPrincipal]: built-in principal implementation to mark system internal subjects.

### Configuration

The [PrincipalConfiguration] is the Oak level entry point to obtain a new
[PrincipalManager] or [PrincipalProvider] as well as principal related configuration
options. The default implementation of the [PrincipalManager] interface is based
on Oak API and can equally be used for privilege related tasks in the Oak layer.

Note, that in contrast to Jackrabbit 2.x the system may only have one single principal
provider implementation configured. In order to combine principals from different
sources a implementation that properly handles the different sources is required;
the [CompositePrincipalProvider] is an example that combines multiple implementations.

<!-- references -->

[PrincipalManager]: http://svn.apache.org/repos/asf/jackrabbit/trunk/jackrabbit-api/src/main/java/org/apache/jackrabbit/api/security/principal/PrincipalManager.java
[PrincipalProvider]: /oak/docs/apidocs/org/apache/jackrabbit/oak/spi/security/principal/PrincipalProvider.html
[CompositePrincipalProvider]: /oak/docs/apidocs/org/apache/jackrabbit/oak/spi/security/principal/CompositePrincipalProvider.html
[AdminPrincipal]: /oak/docs/apidocs/org/apache/jackrabbit/oak/spi/security/principal/AdminPrincipal.html
[EveryonePrincipal]: /oak/docs/apidocs/org/apache/jackrabbit/oak/spi/security/principal/EveryonePrincipal.html
[SystemPrincipal]: /oak/docs/apidocs/org/apache/jackrabbit/oak/spi/security/principal/SystemPrincipal.html
[PrincipalConfiguration]: /oak/docs/apidocs/org/apache/jackrabbit/oak/spi/security/principal/PrincipalConfiguration.html
