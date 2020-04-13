# for mac os

conda create -y --name py37 python=3.7.0
conda clean -ya
CONDA_DEFAULT_ENV=py37
CONDA_PREFIX=/Users/lgu/anaconda3/envs/$CONDA_DEFAULT_ENV

PATH=$CONDA_PREFIX/bin:$PATH
conda install conda-build=3.18.9=py37_3  && conda clean -ya
conda install -c pytorch pytorch
conda install -y h5py=2.8.0 \
 && conda clean -ya
pip install h5py-cache==1.0
pip install torchnet==0.0.4
conda install -y requests=2.19.1 \
   && conda clean -ya

conda install -y graphviz=2.40.1 python-graphviz=0.8.4 \
    && conda clean -ya

conda install -y -c menpo opencv3=3.1.0 \
     && conda clean -ya

conda install torchvision -c soumith

conda install -c pytorch pytorch

conda install -c pytorch torchtext

conda install -c conda-forge configargparse

conda install -c allennlp pytorch-pretrained-bert

conda install -c roccqqck sentencepiece
