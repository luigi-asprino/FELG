#!bin/bash
conda create -y --name py36 python=3.6.9 && conda clean -ya
CONDA_DEFAULT_ENV=py36
CONDA_PREFIX=/root/miniconda3/envs/$CONDA_DEFAULT_ENV
PATH=$CONDA_PREFIX/bin:$PATH
conda install conda-build=3.18.9=py36_3 && conda clean -ya
conda install -y -c pytorch cudatoolkit=10.1 "pytorch=1.4.0=py3.6_cuda10.1.243_cudnn7.6.3_0" "torchvision=0.5.0=py36_cu101" && conda clean -ya
conda install -y h5py=2.8.0 && conda clean -ya
pip install h5py-cache==1.0
pip install torchnet==0.0.4
conda install -y requests=2.19.1 && conda clean -ya
conda install -y graphviz=2.40.1 python-graphviz=0.8.4 && conda clean -ya
apt-get update && apt-get install -y --no-install-recommends libgtk2.0-0 libcanberra-gtk-module
conda install -y -c menpo opencv3=3.1.0 && conda clean -ya
conda install -c pytorch torchtext
conda install -c conda-forge configargparse
conda install -c powerai sentencepiece
conda install -c conda-forge pytorch-pretrained-bert