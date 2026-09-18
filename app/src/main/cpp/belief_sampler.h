// belief_sampler.h
//
// Self-disabling logit-bias sampler.
// Mirrors the Python BeliefProcessor: fires once on the first generation
// step, adds `offset` to the target token's logit, then becomes a no-op.
//
// llama.cpp's built-in logit-bias sampler applies on every step, which
// would cause the model to loop on the target token. This one has the
// correct shape for the belief layer.

#pragma once

#include <llama.h>

struct belief_sampler_state {
    int32_t target_token;
    float   offset;
    bool    applied;
};

static const char * belief_sampler_name(const struct llama_sampler * /*smpl*/) {
    return "belief-offset";
}

static void belief_sampler_apply(
    struct llama_sampler * smpl,
    llama_token_data_array * cur_p)
{
    auto * state = static_cast<belief_sampler_state *>(smpl->ctx);
    if (state->applied) return;

    for (size_t i = 0; i < cur_p->size; ++i) {
        if (cur_p->data[i].id == state->target_token) {
            cur_p->data[i].logit += state->offset;
            break;
        }
    }
    state->applied = true;
}

static void belief_sampler_reset(struct llama_sampler * smpl) {
    auto * state = static_cast<belief_sampler_state *>(smpl->ctx);
    state->applied = false;
}

static void belief_sampler_free(struct llama_sampler * smpl) {
    auto * state = static_cast<belief_sampler_state *>(smpl->ctx);
    delete state;
    delete smpl;
}

static struct llama_sampler * llama_sampler_init_belief(
    int32_t target_token,
    float   offset)
{
    auto * state = new belief_sampler_state{target_token, offset, false};

    static struct llama_sampler_i iface = {
        /* .name   = */ belief_sampler_name,
        /* .accept = */ nullptr,
        /* .apply  = */ belief_sampler_apply,
        /* .reset  = */ belief_sampler_reset,
        /* .clone  = */ nullptr,
        /* .free   = */ belief_sampler_free,
    };

    return llama_sampler_init(&iface, state);
}