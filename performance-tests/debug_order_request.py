#!/usr/bin/env python3
"""
Debug script to replicate the working curl request and identify differences with Locust
"""

import requests
import json
from datetime import datetime
import random

def test_manual_request():
    """Test the exact same request that works in curl"""
    
    # Exact same payload structure that worked in curl
    order_data = {
        "orderDate": datetime.now().strftime("%d-%m-%Y__%H:%M:%S:000000"),
        "orderDesc": "Orden de prueba desde debug script",
        "orderFee": 129.99,
        "cart": {
            "cartId": random.randint(1, 100),
            "userId": random.randint(1000, 9999)
        }
    }
    
    # Exact same headers as curl would use
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json"
    }
    
    url = "http://host.docker.internal/api/orders"
    
    print("=== DEBUG ORDER REQUEST ===")
    print(f"URL: {url}")
    print(f"Headers: {json.dumps(headers, indent=2)}")
    print(f"Payload: {json.dumps(order_data, indent=2)}")
    print()
    
    try:
        response = requests.post(url, json=order_data, headers=headers, timeout=10)
        
        print(f"Status Code: {response.status_code}")
        print(f"Response Headers: {dict(response.headers)}")
        print(f"Response Body: {response.text}")
        
        if response.status_code in [200, 201]:
            print("✅ SUCCESS: Order created successfully!")
            try:
                response_json = response.json()
                order_id = response_json.get('orderId', response_json.get('id'))
                print(f"Created Order ID: {order_id}")
            except:
                print("Response is not JSON")
        else:
            print("❌ FAILED: Order creation failed")
            
    except Exception as e:
        print(f"❌ ERROR: {str(e)}")

def test_locust_style_request():
    """Test using the same approach as Locust"""
    
    # Same payload as Locust test
    user_id = random.randint(1000, 9999)
    order_data = {
        "orderDate": datetime.now().strftime("%d-%m-%Y__%H:%M:%S:000000"),
        "orderDesc": f"Orden de 1 producto(s) - testuser_{user_id}",
        "orderFee": 129.99,
        "cart": {
            "cartId": random.randint(1, 100),
            "userId": user_id
        }
    }
    
    # Same headers as Locust test
    headers = {
        "Content-Type": "application/json",
        "User-Agent": "LoadTest-OrderCreation/1.0",
        "X-User-ID": str(user_id)
    }
    
    url = "http://host.docker.internal/api/orders"
    
    print("\n=== LOCUST STYLE REQUEST ===")
    print(f"URL: {url}")
    print(f"Headers: {json.dumps(headers, indent=2)}")
    print(f"Payload: {json.dumps(order_data, indent=2)}")
    print()
    
    try:
        response = requests.post(url, json=order_data, headers=headers, timeout=10)
        
        print(f"Status Code: {response.status_code}")
        print(f"Response Headers: {dict(response.headers)}")
        print(f"Response Body: {response.text}")
        
        if response.status_code in [200, 201]:
            print("✅ SUCCESS: Order created successfully!")
            try:
                response_json = response.json()
                order_id = response_json.get('orderId', response_json.get('id'))
                print(f"Created Order ID: {order_id}")
            except:
                print("Response is not JSON")
        else:
            print("❌ FAILED: Order creation failed")
            
    except Exception as e:
        print(f"❌ ERROR: {str(e)}")

if __name__ == "__main__":
    test_manual_request()
    test_locust_style_request()
