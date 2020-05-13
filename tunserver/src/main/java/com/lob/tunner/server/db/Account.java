package com.lob.tunner.server.db;

import java.util.TimeZone;

/**
 * Like other objects, we use an NoSQL approach to store
 * accounts in the MySQL table.
 *
 * An account object has an id that is a 64-bit long generated using
 * the snowflake algorithm and global-wise unique.
 *
 * The name is a single word string such as "cnn", "netflix". It's
 * case-insensitive.
 *
 * Everything else is stored in a JSON object whose corresponding
 * table column is "properties". The JSON object has the following
 * format:
 *
 * {
 *     ver: 1,
 *     status: "ready", // the value could be
 *                      // "ready"|"suspended"|"terminated"|"settingUp1"
 *                      // "settingUp1" means our admin finishes the
 *                      // initial setup and waits for the user enters
 *                      // more information to finish the entire setup
 *                      // process
 *     createdAtEpochMs: 454549454, // when this account was created
 *     createdByAdmin: "abc@zervice.us", // which internal employee initialize
 *                                         // the creation of the account
 *     createdByUser: "def@cnn.com",       // which account employee finishes
 *                                         // the account setup
 *     setupFinishedAtEpochMs: 5684054,    // when the account setup is finished
 *                                         // by the account employee
 *     timezone: "America/Los_Angeles"     // account's timezone
 * }
 *
 * So the JSON object returned by RESTful API has the format
 * {
 *     id: "a34344", // "a" + Base33(id)
 *     name: "cnn",
 *     properties: {
 *         ver: 1,
 *         status: "ready",
 *         createdAtEpochMs: 4095409584,
 *         createdByAdmin: "abc@zervice.us",
 *         createdByUser: "def@cnn.com",
 *         setupFinishedAtEpochMs: 45453453,
 *         timezone: "America/Los_Angeles"
 *     }
 * }
 */
public class Account {
    public static final String STATUS_READY = "ready";
    public static final String STATUS_SUSPENDED = "suspended";
    public static final String STATUS_TERMINATED = "terminated";
    public static final String STATUS_SETTINGUP1 = "settingUp1";

    private static final String[] _VALID_STATUS = new String[]{
        STATUS_READY, STATUS_SETTINGUP1, STATUS_SUSPENDED, STATUS_TERMINATED
    };


    long _id;

    String _name;

    String _properties;

    public long getId() {
        return _id;
    }

    public void setId(long id) {
        _id = id;
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public String getProperties() {
        return _properties;
    }

    public void setProperties(String props) {
        _properties = props;
    }

    public String getExternalId() {
        return "Account-" + _id;
    }

    /**
     * Generate the db name of the account from its id. The db name is
     *   'a' + Base36Encoding(id)
     */
    public String getAccountDbName() {
        return getExternalId();
    }

    public static boolean isValidStatus(String status) {
        for (int i = 0; i < _VALID_STATUS.length; i++) {
            if (status.equals(_VALID_STATUS[i])) {
                return true;
            }
        }

        return false;
    }
}
