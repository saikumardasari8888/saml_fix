package com.saparate.pc.config;

import java.io.Serializable;

public class PCConnConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    private String username;
    private String password;
    private String url;


    public PCConnConfig(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public String getURL() {
        return this.url;
    }

    public String getUsername() {
        return this.username;
    }

    public String getPassword() {
        return this.password;
    }

}

