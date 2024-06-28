// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import java.util.List;
import lombok.Getter;

@Getter
public class LoadBalancing {
  List<Server> servers;
}
