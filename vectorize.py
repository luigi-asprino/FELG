import collections
import torch
import time
import pickle5 as pickle
import re
import json_lines
import os
import string
punct = [x for x in string.punctuation]

torch.set_grad_enabled(False)
from transformers import *

def dict_merge(dct, merge_dct):
    """ Recursive dict merge. Inspired by :meth:``dict.update()``, instead of
    updating only top-level keys, dict_merge recurses down into dicts nested
    to an arbitrary depth, updating keys. The ``merge_dct`` is merged into
    ``dct``.
    :param dct: dict onto which the merge is executed
    :param merge_dct: dct merged into dct
    :return: None
    """
    for k, v in merge_dct.items():
        if (k in dct and isinstance(dct[k], dict)
                and isinstance(merge_dct[k], collections.Mapping)):
            dict_merge(dct[k], merge_dct[k])
        else:
            dct[k] = merge_dct[k]
    return dct


######## TRANSFORMERS ##############

class InputFeatures(object):
    """A single set of features of data."""
    def __init__(self, unique_id, tokens, input_ids, input_mask, input_type_ids):
        self.unique_id = unique_id
        self.tokens = tokens
        self.input_ids = input_ids
        self.input_mask = input_mask
        self.input_type_ids = input_type_ids


def convert_examples_to_features(examples, seq_length, tokenizer):
    """Loads a data file into a list of `InputBatch`s."""
    features = []
    for (ex_index, example) in enumerate(examples):
        tokens_a = tokenizer.tokenize(example.text_a)

        #tokens_b = None
        #if example.text_b:
        #    tokens_b = tokenizer.tokenize(example.text_b)

        #if tokens_b:
            # Modifies `tokens_a` and `tokens_b` in place so that the total
            # length is less than the specified length.
            # Account for [CLS], [SEP], [SEP] with "- 3"
        #    _truncate_seq_pair(tokens_a, tokens_b, seq_length - 3)
        #else:
            # Account for [CLS] and [SEP] with "- 2"
        if len(tokens_a) > seq_length - 2:
            tokens_a = tokens_a[0:(seq_length - 2)]
        tokens = []
        input_type_ids = []
        tokens.append("[CLS]")
        input_type_ids.append(0)
        for token in tokens_a:
            tokens.append(token)
            input_type_ids.append(0)
        tokens.append("[SEP]")
        input_type_ids.append(0)

        # if tokens_b:
        #     for token in tokens_b:
        #         tokens.append(token)
        #         input_type_ids.append(1)
        #     tokens.append("[SEP]")
        #     input_type_ids.append(1)

        input_ids = tokenizer.convert_tokens_to_ids(tokens)

        # The mask has 1 for real tokens and 0 for padding tokens. Only real
        # tokens are attended to.
        input_mask = [1] * len(input_ids)

        # Zero-pad up to the sequence length.
        while len(input_ids) < seq_length:
            input_ids.append(0)
            input_mask.append(0)
            input_type_ids.append(0)

        assert len(input_ids) == seq_length
        assert len(input_mask) == seq_length
        assert len(input_type_ids) == seq_length

        if ex_index < 5:
            logger.info("*** Example ***")
            logger.info("unique_id: %s" % (example.unique_id))
            logger.info("tokens: %s" % " ".join([str(x) for x in tokens]))
            logger.info("input_ids: %s" % " ".join([str(x) for x in input_ids]))
            logger.info("input_mask: %s" % " ".join([str(x) for x in input_mask]))
            logger.info(
                "input_type_ids: %s" % " ".join([str(x) for x in input_type_ids]))

        features.append(
            InputFeatures(
                unique_id=example.unique_id,
                tokens=tokens,
                input_ids=input_ids,
                input_mask=input_mask,
                input_type_ids=input_type_ids))
    return features


def _truncate_seq_pair(tokens_a, max_length):
    """Truncates a sequence pair in place to the maximum length."""

    # This is a simple heuristic which will always truncate the longer sequence
    # one token at a time. This makes more sense than truncating an equal percent
    # of tokens from each, since if one sequence is very short then each token
    # that's truncated likely contains more information than a longer sequence.
    while True:
        total_length = len(tokens_a)
        if total_length <= max_length:
            break
        #if len(tokens_a) > len(tokens_b):
        tokens_a.pop()
        #else:
        #    tokens_b.pop()


def read_examples(input_file):
    """Read a list of `InputExample`s from an input file."""
    examples = []
    unique_id = 0
    with open(input_file, "r", encoding='utf-8') as reader:
        while True:
            line = reader.readline()
            if not line:
                break
            line = line.strip()
            #text_b = None
            m = re.match(r"^(.*) \|\|\| (.*)$", line)
            if m is None:
                text_a = line
            else:
                text_a = m.group(1)
                #text_b = m.group(2)
            examples.append(
                InputExample(unique_id=unique_id, text_a=text_a))
            unique_id += 1
    return examples



def get_transformer_vectors(all_features, sent_indices):
    vectors = {}
    for isent, all_transf_features in enumerate(all_features):
        vectors[sent_indices[isent]] = dict()
        ww_transf = [out_features['token'] for out_features in all_transf_features]
        Idx = []
        for idx_ww_transf, ww in enumerate(ww_transf[1:-1]):
            Idx.append([idx_ww_transf])
        for idx, token in enumerate(ww_transf[1:-1]):
            vec = torch.mean(torch.tensor([all_transf_features[i]['layers'][-1]['values'] for i in Idx[idx]]),0)
            vectors[sent_indices[isent]][idx] = {'vec': vec, 'token': token}
    return vectors



def clean_text(text):
    pat = re.compile("&lt;.*?&gt;")
    text_ = pat.sub('', text)
    return text_




def text2sents(text, nlp):
    doc = nlp(text)
    sents = [s.text for s in doc.sents]
    sents_spacy = [[t.text for t in s] for s in doc.sents]
    return [sents, sents_spacy]

def get_transformer(sents, sents_spacy, model,tokenizer,batch_size, d):
    #device = torch.device("cuda")
    layers_idxs = [12, 22]
    #device = torch.device("cpu")
    device = torch.device(d)
    model.eval()
    model.to(device)
    Vectors = dict()
    with torch.no_grad():
        Sents = []
        indices = [x for x in range(0,len(sents),int(batch_size))] + [len(sents)]
        for idx in range(len(indices[:-1])):
            sents_ = sents[indices[idx]:indices[idx+1]]
            tokens = [tokenizer.tokenize(sent) for sent in sents_]
            Sents+=[sents_[i] for i, s in enumerate(tokens) if len(tokens[i]) < 511]
            tokens = [t for t in tokens if len(t) < 511]
            #print(tokens)
            tokens_ids = [tokenizer.convert_tokens_to_ids(toks) for toks in tokens]
            tokens_ids = [tokenizer.build_inputs_with_special_tokens(toks_ids) for toks_ids in tokens_ids]
            max_len = max([len(t) for t in tokens_ids])
            model.config.max_position_embeddings = max_len
            model.eval()
            model.to(device)
            for xx in range(len(tokens_ids)):
                tokens[xx] = ["[CLS]"] + tokens[xx]
                tokens[xx].append("[SEP]")
                while len(tokens_ids[xx]) < max_len:
                    tokens_ids[xx].append(0)
            tokens_pt = torch.tensor(tokens_ids).to(device)
            #start_time = time.time()
            All_hidden_states = model(tokens_pt)
            #time_emb = time.time() - start_time
            #start_time = time.time()
            #all_hidden_states = all_hidden_states.detach().cpu().numpy()
            #time_det = time.time() - start_time
            #print("{0:3d}\t{1:3d}\t{2:3d}\t{3:.4f}\t{4:.2f}".format(len(sents[:indices[idx + 1]]), len(sents), max_len, time_emb, time_det))
            for layer_idx in layers_idxs:
                Vectors.setdefault(layer_idx, {})
                all_hidden_states = All_hidden_states['hidden_states'][layer_idx].detach().cpu().numpy()
                All_out_features = []
                for b, example_index in enumerate(tokens_ids):
                    all_out_features = []
                    layer_output = all_hidden_states[b]
                    for (i, token) in enumerate(tokens[b]):
                        all_layers = []
                        layers = collections.OrderedDict()
                        layers["index"] = layer_idx
                        layers["values"] = layer_output[i]
                        all_layers.append(layers)
                        out_features = collections.OrderedDict()
                        out_features["token"] = token
                        out_features["layers"] = all_layers
                        all_out_features.append(out_features)
                    All_out_features.append(all_out_features)
                vectors = get_transformer_vectors(All_out_features, range(indices[idx],indices[idx+1]))
                Vectors[layer_idx].update(vectors)
    return Vectors

if __name__ == '__main__':
    from tqdm import tqdm
    from argparse import ArgumentParser
    import spacy
    parser = ArgumentParser(description='Script to vectorize raw text.')
    #args = parser.parse_args()
    parser.add_argument(
        '-d', '--device', default='cuda',
        help='Device to use. (cpu, cuda, cuda:0 etc.)')
    parser.add_argument(
        '-b', '--batch_size', default=100,
        help='Number of sentences to vectorise simultaniusly')
    parser.add_argument(
        '-mdl', '--model', default="roberta-large",
        help='Name of the HuggingFace model')
    parser.add_argument(
        '-smdl', '--spacy_model', default="en_core_web_sm",
        help='Name of the SpaCy model')
    parser.add_argument(
        '-wp', '--wiki_path', default="/media/hdd/wiki/wiki_20210520_json_links",
        help='Name of the SpaCy model')
    args = parser.parse_args()
    wiki_path = args.wiki_path
    batch_size = args.batch_size
    pretrained_weights = args.model
    model_class = AutoModel.from_pretrained(pretrained_weights, output_hidden_states=True)
    tokenizer_class = AutoTokenizer.from_pretrained(pretrained_weights)
    nlp = spacy.load(args.spacy_model, disable=['ner', 'parser'])
    nlp.add_pipe('sentencizer')
    folders = [f for f in os.listdir(wiki_path) if os.path.isdir(wiki_path+ '/' + f)]
    folders.sort()
    for folder in tqdm(folders):
        print(folder)
        files_ = [f for f in os.listdir(os.path.join(wiki_path,folder)) if '.' not in f and f+'_vectors.pkl' not in os.listdir(os.path.join(wiki_path,folder))]
        files_.sort()
        for file_ in tqdm(files_):
            Vectors = {}
            with open(os.path.join(wiki_path, folder, file_)) as rf:
                for i, item in enumerate(json_lines.reader(rf)):
                    if len(item['text']) < 200:
                        continue
                    Vectors[item['id']] = {}
                    cleaned_text = clean_text(item['text'])
                    sents, sents_spacy = text2sents(cleaned_text, nlp)
                    vectors = get_transformer(sents, sents_spacy, model_class, tokenizer_class, batch_size, args.device)
                    Vectors[item['id']]['vectors'] = vectors
                    Vectors[item['id']]['title'] = item['title']
                    Vectors[item['id']]['url'] = item['url']

            with open(os.path.join(wiki_path, folder, file_) + '_vectors.pkl', 'wb') as wf:
                pickle.dump(Vectors, wf)
