package com.app.service;

record StockImportFailure(int rowNumber, String productCode, String color, String message) {}
