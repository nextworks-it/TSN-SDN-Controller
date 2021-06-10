/*
 * Copyright © 2018 2020 and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package it.nextworks.provisioningappcomplete.cli.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import it.nextworks.provisioningappcomplete.cli.api.ProvisioningappcompleteCliCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProvisioningappcompleteCliCommandsImpl implements ProvisioningappcompleteCliCommands {

    private static final Logger LOG = LoggerFactory.getLogger(ProvisioningappcompleteCliCommandsImpl.class);
    private final DataBroker dataBroker;

    public ProvisioningappcompleteCliCommandsImpl(final DataBroker db) {
        this.dataBroker = db;
        LOG.info("ProvisioningappcompleteCliCommandImpl initialized");
    }

    @Override
    public Object testCommand(Object testArgument) {
        return "This is a test implementation of test-command";
    }
}
