#!/usr/bin/env python3
"""
Fraud Detection Prediction Script
Called by Spring Boot to get ML fraud predictions
"""

import sys
import joblib
import numpy as np
import warnings
warnings.filterwarnings('ignore')

# Load models (do this once at startup ideally)
try:
    model = joblib.load('fraud_model_best.pkl')
    scaler = joblib.load('fraud_scaler.pkl')
    feature_names = joblib.load('fraud_features.pkl')
except Exception as e:
    print(f"0.5", file=sys.stderr)  # Return default if model load fails
    print(f"Error loading model: {e}", file=sys.stderr)
    sys.exit(1)

def predict_fraud(features):
    """
    Predict fraud probability for given features
    
    Args:
        features: List of feature values in correct order
    
    Returns:
        Fraud probability (0.0 to 1.0)
    """
    try:
        # Convert to numpy array
        features_array = np.array([features], dtype=float)
        
        # Get prediction probability
        fraud_prob = model.predict_proba(features_array)[0][1]
        
        return fraud_prob
        
    except Exception as e:
        print(f"Error in prediction: {e}", file=sys.stderr)
        return 0.5  # Default middle value on error

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("0.5")  # Default value
        sys.exit(0)
    
    # Parse features from command line
    features_str = sys.argv[1]
    features = [float(x) for x in features_str.split(',')]
    
    # Validate feature count
    if len(features) != len(feature_names):
        print(f"0.5", file=sys.stderr)
        print(f"Error: Expected {len(feature_names)} features, got {len(features)}", file=sys.stderr)
        sys.exit(1)
    
    # Get prediction
    fraud_prob = predict_fraud(features)
    
    # Output only the probability (Spring Boot will read this)
    print(f"{fraud_prob:.4f}")
