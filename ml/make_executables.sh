#!/bin/bash
conda activate testing

cd python-scripts

pyinstaller --onefile --hidden-import="sklearn.utils._cython_blas" --hidden-import="sklearn.neighbors.typedefs" --hidden-import="sklearn.neighbors.quad_tree" --hidden-import="sklearn.tree._utils" --hidden-import="sklearn.utils._typedefs" --hidden-import="sklearn.neighbors._partition_nodes" --hidden-import="sklearn.utils._weight_vector" --hidden-import="sklearn.neighbors._quad_tree" --clean train_predictor.py
pyinstaller --onefile --hidden-import="sklearn.utils._cython_blas" --hidden-import="sklearn.neighbors.typedefs" --hidden-import="sklearn.neighbors.quad_tree" --hidden-import="sklearn.tree._utils" --hidden-import="sklearn.utils._typedefs" --hidden-import="sklearn.neighbors._partition_nodes" --hidden-import="sklearn.utils._weight_vector" --hidden-import="sklearn.neighbors._quad_tree" --clean al_predictor.py
pyinstaller --onefile --hidden-import="sklearn.utils._cython_blas" --hidden-import="sklearn.neighbors.typedefs" --hidden-import="sklearn.neighbors.quad_tree" --hidden-import="sklearn.tree._utils" --hidden-import="sklearn.utils._typedefs" --hidden-import="sklearn.neighbors._partition_nodes" --hidden-import="sklearn.utils._weight_vector" --hidden-import="sklearn.neighbors._quad_tree" --clean ml_predictor.py

mv dist/train_predictor ../mlTrain
mv dist/al_predictor ../mlUncertaintyPredictor
mv dist/ml_predictor ../mlValidityPredictor

rm -rf build
rm -rf dist
rm -f train_predictor.spec
rm -f al_predictor.spec
rm -f ml_predictor.spec

chmod u+x ../mlTrain
chmod u+x ../mlUncertaintyPredictor
chmod u+x ../mlValidityPredictor

cd ..